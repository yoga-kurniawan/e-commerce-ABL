package id.ac.ub.abl.payment.adapter.outbound.database;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentIdempotencyId;
import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentJpaEntity;
import id.ac.ub.abl.payment.domain.PaymentMethod;
import id.ac.ub.abl.payment.domain.PaymentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(PaymentDatabaseSession.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentRepositoriesTest {
    private static final String OPERATION = "CREATE_PAYMENT_V1";
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired PaymentsRepository payments;
    @Autowired PaymentOrderGuardsRepository guards;
    @Autowired PaymentIdempotencyRepository idempotency;
    @Autowired PaymentDatabaseSession session;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearDisposableDatabase() {
        jdbc.execute("TRUNCATE payment_idempotency, payments, payment_order_guards");
    }

    @Test
    void paymentQueries_whenHistoryExists_shouldFindOnlyBlockingAttemptAndExactAmount() {
        transactions().executeWithoutResult(tx -> {
            payments.saveAndFlush(new PaymentJpaEntity(501L, PaymentMethod.COD, BigDecimal.ZERO,
                    PaymentStatus.FAILED, null, session.now()));
            var pending = payments.saveAndFlush(new PaymentJpaEntity(501L, PaymentMethod.TRANSFER,
                    new BigDecimal("350000.12500"), PaymentStatus.PENDING, "  reference  ", session.now()));
            entityManager.clear();
            assertThat(payments.findBlockingAttempt(501L)).hasValueSatisfying(found -> {
                assertThat(found.getId()).isEqualTo(pending.getId());
                assertThat(found.getAmount()).isEqualTo(new BigDecimal("350000.12500"));
                assertThat(found.getTransactionReference()).isEqualTo("  reference  ");
            });
            assertThat(payments.existsByOrderIdAndStatus(501L, PaymentStatus.PENDING)).isTrue();
            assertThat(payments.existsByOrderIdAndStatus(501L, PaymentStatus.SUCCESS)).isFalse();
            assertThat(payments.findOrderIdById(pending.getId())).contains(501L);
            assertThat(payments.findById(-999L)).isEmpty();
            assertThat(payments.findOrderIdById(-999L)).isEmpty();
            assertThat(payments.findBlockingAttempt(999L)).isEmpty();
            var managed = payments.findByIdForUpdate(pending.getId()).orElseThrow();
            managed.updateState(PaymentStatus.SUCCESS, managed.getTransactionReference(), session.now());
            payments.flush();
            assertThat(payments.findBlockingAttempt(501L)).hasValueSatisfying(found ->
                    assertThat(found.getStatus()).isEqualTo(PaymentStatus.SUCCESS));
        });
    }

    @Test
    void guardInsert_whenDuplicate_shouldKeepTransactionUsableAndReturnSameGuard() {
        transactions().executeWithoutResult(tx -> {
            session.configureLockTimeout(Duration.ofMillis(250));
            assertThat(guards.insertIfAbsent(501L)).isEqualTo(1);
            assertThat(guards.insertIfAbsent(501L)).isZero();
            assertThat(guards.findByOrderIdForUpdate(501L)).hasValueSatisfying(guard ->
                    assertThat(guard.getOrderId()).isEqualTo(501L));
            assertThat(guards.findByOrderIdForUpdate(999L)).isEmpty();
            assertThat(session.now()).isNotNull();
        });
    }

    @Test
    void claimInsert_whenKeyExists_shouldPreserveOriginalPayloadAndSupportCaseSensitiveLookup() {
        transactions().executeWithoutResult(tx -> {
            assertThat(insertClaim("Key", 501L, "owner")).isEqualTo(1);
            var now = session.now();
            assertThat(insertClaim("Key", 502L, "other")).isZero();
            assertThat(insertClaim("key", 502L, "other")).isEqualTo(1);
            assertThat(idempotency.findById(key("Key"))).hasValueSatisfying(claim -> {
                assertThat(claim.getOrderId()).isEqualTo(501L);
                assertThat(claim.getOwnerToken()).isEqualTo("owner");
                assertThat(claim.getLeaseUntil()).isAfter(now).isBefore(now.plusSeconds(31));
            });
            assertThat(idempotency.findById(key("key"))).isPresent();
            assertThat(idempotency.findById(key("missing"))).isEmpty();
        });
    }

    @Test
    void lookup_whenPaymentChanged_shouldReturnOriginalCompletedSnapshot() {
        Long paymentId = seedPayment();
        seedCompleted("replay", paymentId, false);
        jdbc.update("UPDATE payments SET status = 'SUCCESS' WHERE id = ?", paymentId);
        assertThat(idempotency.findById(key("replay"))).hasValueSatisfying(claim -> {
            assertThat(claim.getPaymentId()).isEqualTo(paymentId);
            assertThat(claim.getResponseStatus()).isEqualTo(201);
            assertThat(claim.getResponseContentType()).isEqualTo("application/json");
            assertThat(claim.getResponseBody()).isEqualTo("{\n  \"status\":\"PENDING\"\n}");
        });
    }

    @Test
    void release_whenOwnerChangedOrCompleted_shouldNeverDeleteNewOwnerOrSnapshot() {
        transactions().executeWithoutResult(tx -> {
            insertClaim("owned", 501L, "new-owner");
            assertThat(idempotency.deleteProcessingOwnedBy(OPERATION, "owned", "old-owner")).isZero();
            assertThat(idempotency.deleteProcessingOwnedBy(OPERATION, "owned", "new-owner")).isEqualTo(1);
        });
        seedCompleted("done", seedPayment(), false);
        transactions().executeWithoutResult(tx -> {
            assertThat(idempotency.deleteProcessingOwnedBy(OPERATION, "done", "owner")).isZero();
            assertThat(idempotency.deleteExpiredCompletedByKey(OPERATION, "done")).isZero();
        });
    }

    @Test
    void cleanup_whenBounded_shouldDeleteOnlyExpiredCompletedAndRetainPayment() {
        Long id = seedPayment();
        seedCompleted("old-1", id, true);
        seedCompleted("old-2", id, true);
        seedCompleted("fresh", id, false);
        transactions().executeWithoutResult(tx -> insertClaim("processing", 501L, "owner"));
        jdbc.update("UPDATE payment_idempotency SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE key = 'processing'");
        transactions().executeWithoutResult(tx -> {
            assertThat(idempotency.deleteExpiredCompletedBatch(1)).isEqualTo(1);
            assertThat(idempotency.deleteExpiredCompletedBatch(10)).isEqualTo(1);
            assertThat(idempotency.deleteExpiredCompletedBatch(10)).isZero();
        });
        assertThat(idempotency.findById(key("fresh"))).isPresent();
        assertThat(idempotency.findById(key("processing"))).isPresent();
        assertThat(payments.findById(id)).isPresent();
    }

    @Test
    void expiredReplacement_whenRolledBack_shouldPreserveOldSnapshot() {
        seedCompleted("expired", seedPayment(), true);
        transactions().executeWithoutResult(tx -> {
            idempotency.findByIdForUpdate(key("expired")).orElseThrow();
            assertThat(idempotency.deleteExpiredCompletedByKey(OPERATION, "expired")).isEqualTo(1);
            assertThat(insertClaim("expired", 999L, "new-owner")).isEqualTo(1);
            // Native delete/insert melewati persistence context; jangan membaca entity lama dari cache.
            entityManager.clear();
            assertThat(idempotency.findById(key("expired"))).hasValueSatisfying(row ->
                    assertThat(row.getOrderId()).isEqualTo(999L));
            tx.setRollbackOnly();
        });
        assertThat(idempotency.findById(key("expired"))).hasValueSatisfying(row ->
                assertThat(row.getResponseStatus()).isEqualTo(201));
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "guard", "idempotency"})
    void locks_whenHeldByAnotherTransaction_shouldRespectBudgetAndReleaseAfterCommit(String resource) throws Exception {
        Long id = seedPayment();
        transactions().executeWithoutResult(tx -> {
            guards.insertIfAbsent(501L);
            insertClaim("locked", 501L, "owner");
        });
        var worker = Executors.newSingleThreadExecutor();
        try {
            transactions().executeWithoutResult(tx -> {
                lock(resource, id);
                try {
                    Throwable failure = worker.submit(() -> catchThrowable(() -> transactions().executeWithoutResult(other -> {
                        session.configureLockTimeout(Duration.ofMillis(250));
                        lock(resource, id);
                    }))).get(10, TimeUnit.SECONDS);
                    assertThat(failure).isInstanceOf(DataAccessException.class);
                    Throwable root = failure;
                    while (root.getCause() != null) { root = root.getCause(); }
                    assertThat(root).isInstanceOfSatisfying(SQLException.class,
                            sql -> assertThat(sql.getSQLState()).isEqualTo("55P03"));
                } catch (Exception error) { throw new AssertionError(error); }
                assertThat(payments.findById(id)).isPresent();
            });
            transactions().executeWithoutResult(tx -> lock(resource, id));
        } finally { worker.shutdownNow(); }
    }

    @Test
    void cleanup_whenExpiredRowLocked_shouldSkipItWithoutDeletingProcessing() throws Exception {
        seedCompleted("locked", seedPayment(), true);
        var worker = Executors.newSingleThreadExecutor();
        try {
            transactions().executeWithoutResult(tx -> {
                idempotency.findByIdForUpdate(key("locked")).orElseThrow();
                try {
                    assertThat(worker.submit(() -> transactions().execute(other -> {
                        session.configureLockTimeout(Duration.ofMillis(250));
                        return idempotency.deleteExpiredCompletedBatch(10);
                    })).get(10, TimeUnit.SECONDS)).isZero();
                } catch (Exception error) { throw new AssertionError(error); }
            });
            transactions().executeWithoutResult(tx -> assertThat(idempotency.deleteExpiredCompletedBatch(10)).isEqualTo(1));
        } finally { worker.shutdownNow(); }
    }

    @Test
    void lockingAndMutation_whenTransactionMissing_shouldFailBeforeExecutingSql() {
        assertThatThrownBy(() -> payments.findByIdForUpdate(1L)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> guards.insertIfAbsent(1L)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> idempotency.findByIdForUpdate(key("key"))).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> session.configureLockTimeout(Duration.ofMillis(250)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> transactions().executeWithoutResult(tx -> session.configureLockTimeout(Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void lock(String resource, Long id) {
        switch (resource) {
            case "payment" -> payments.findByIdForUpdate(id).orElseThrow();
            case "guard" -> guards.findByOrderIdForUpdate(501L).orElseThrow();
            case "idempotency" -> idempotency.findByIdForUpdate(key("locked")).orElseThrow();
            default -> throw new IllegalArgumentException(resource);
        }
    }

    private TransactionTemplate transactions() { return new TransactionTemplate(transactionManager); }
    private PaymentIdempotencyId key(String value) { return new PaymentIdempotencyId(OPERATION, value); }
    private int insertClaim(String key, Long order, String owner) {
        return idempotency.insertProcessingIfAbsent(OPERATION, key, order, "COD", "a".repeat(64), owner, 30000);
    }
    private Long seedPayment() {
        return transactions().execute(tx -> payments.saveAndFlush(new PaymentJpaEntity(501L, PaymentMethod.COD,
                BigDecimal.ONE, PaymentStatus.PENDING, null, session.now())).getId());
    }
    private void seedCompleted(String key, Long payment, boolean expired) {
        jdbc.update("""
                INSERT INTO payment_idempotency (operation, key, order_id, method, fingerprint, state,
                    owner_token, lease_until, payment_id, response_status, response_content_type,
                    response_body, completed_at, expires_at)
                VALUES (?, ?, 501, 'COD', ?, 'COMPLETED', 'owner', CURRENT_TIMESTAMP, ?, 201,
                    'application/json', ?, CURRENT_TIMESTAMP - (? * INTERVAL '1 hour'),
                    CURRENT_TIMESTAMP - (? * INTERVAL '1 hour') + INTERVAL '24 hours')
                """, OPERATION, key, "a".repeat(64), payment, "{\n  \"status\":\"PENDING\"\n}",
                expired ? 25 : 0, expired ? 25 : 0);
    }
}
