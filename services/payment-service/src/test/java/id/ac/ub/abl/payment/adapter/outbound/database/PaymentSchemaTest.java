package id.ac.ub.abl.payment.adapter.outbound.database;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import id.ac.ub.abl.payment.adapter.outbound.database.entity.IdempotencyState;
import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentIdempotencyId;
import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentIdempotencyJpaEntity;
import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentJpaEntity;
import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentOrderGuardJpaEntity;
import id.ac.ub.abl.payment.domain.PaymentMethod;
import id.ac.ub.abl.payment.domain.PaymentStatus;
import id.ac.ub.abl.payment.usecase.command.CreatePaymentCommand;
import id.ac.ub.abl.payment.usecase.model.IdempotencyKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Slice JPA + Flyway asli, hanya menyentuh database disposable Testcontainers. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentSchemaTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void clearDisposableDatabase() {
        jdbc.execute("TRUNCATE payment_idempotency, payments, payment_order_guards");
    }

    @Test
    void migration_whenRunAgain_shouldValidateAndApplyNoChanges() {
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE contype = 'f' AND conrelid IN
                    ('payments'::regclass, 'payment_order_guards'::regclass, 'payment_idempotency'::regclass)
                """, Integer.class)).isEqualTo(1); // hanya payment_id -> payments, bukan Order
        assertThat(jdbc.queryForObject("""
                SELECT confrelid::regclass::text FROM pg_constraint
                WHERE conname = 'fk_payment_idempotency_payment'
                """, String.class)).isEqualTo("payments");
        assertThat(jdbc.queryForObject("""
                SELECT atttypmod FROM pg_attribute
                WHERE attrelid = 'payments'::regclass AND attname = 'amount'
                """, Integer.class)).isEqualTo(-1); // NUMERIC tanpa precision/scale tetap
    }

    @ParameterizedTest
    @ValueSource(strings = {"350000.12500", "0", "123456789012345678901234567890.123456789"})
    void mapping_whenAmountHasArbitraryScale_shouldRoundTripWithoutRounding(String value) {
        BigDecimal amount = new BigDecimal(value);
        String reference = "  bukti pembayaran\n" + "x".repeat(1000);
        Instant now = databaseNow();
        Long id = transactions().execute(tx -> {
            var payment = new PaymentJpaEntity(501L, PaymentMethod.E_WALLET, amount,
                    PaymentStatus.PENDING, reference, now);
            assertThat(payment.getVersion()).isNull();
            entityManager.persist(payment);
            entityManager.flush();
            assertThat(payment.getVersion()).isZero();
            return payment.getId();
        });
        transactions().executeWithoutResult(tx -> {
            var stored = entityManager.find(PaymentJpaEntity.class, id);
            assertThat(stored.getAmount()).isEqualByComparingTo(amount);
            assertThat(stored.getAmount().scale()).isEqualTo(amount.scale());
            assertThat(stored.getOrderId()).isEqualTo(501L);
            assertThat(stored.getMethod()).isEqualTo(PaymentMethod.E_WALLET);
            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(stored.getTransactionReference()).isEqualTo(reference);
            assertThat(stored.getCreatedAt()).isEqualTo(now);
            assertThat(stored.getUpdatedAt()).isEqualTo(now);
        });
        assertThat(id).isPositive();
    }

    @Test
    void mapping_whenSnapshotCompleted_shouldPreserveExactResponseAndMetadata() {
        Instant now = databaseNow();
        var command = new CreatePaymentCommand(501L, PaymentMethod.COD, new IdempotencyKey("Key.A"));
        var key = new PaymentIdempotencyId(IdempotencyKey.OPERATION, command.key().value());
        String response = "{\n  \"id\":1, \"status\":\"PENDING\", \"amount\":350000.12500\n}";
        transactions().executeWithoutResult(tx -> {
            var guard = new PaymentOrderGuardJpaEntity(501L);
            assertThat(guard.getVersion()).isNull();
            entityManager.persist(guard);
            var claim = new PaymentIdempotencyJpaEntity(key, 501L, PaymentMethod.COD,
                    command.fingerprint(), "owner-1", now.plusSeconds(30));
            entityManager.persist(claim);
            entityManager.flush();
            assertThat(claim.getVersion()).isZero();
            assertThat(guard.getVersion()).isZero();
        });
        Long paymentId = transactions().execute(tx -> {
            var claim = entityManager.find(PaymentIdempotencyJpaEntity.class, key);
            assertThat(claim.getState()).isEqualTo(IdempotencyState.PROCESSING);
            assertThat(claim.getFingerprint()).isEqualTo(command.fingerprint());
            assertThat(claim.getLeaseUntil()).isEqualTo(now.plusSeconds(30));
            claim.updateOwner("owner-2", now.plusSeconds(60));
            var payment = new PaymentJpaEntity(501L, PaymentMethod.COD, new BigDecimal("350000.12500"),
                    PaymentStatus.PENDING, null, now);
            entityManager.persist(payment);
            entityManager.flush();
            claim.recordCompletion(payment.getId(), 201, "application/json", response,
                    now, now.plus(Duration.ofHours(24)));
            return payment.getId();
        });
        transactions().executeWithoutResult(tx -> {
            var stored = entityManager.find(PaymentIdempotencyJpaEntity.class, key);
            assertThat(stored.getState()).isEqualTo(IdempotencyState.COMPLETED);
            assertThat(stored.getOrderId()).isEqualTo(501L);
            assertThat(stored.getMethod()).isEqualTo(PaymentMethod.COD);
            assertThat(stored.getOwnerToken()).isEqualTo("owner-2");
            assertThat(stored.getLeaseUntil()).isEqualTo(now.plusSeconds(60));
            assertThat(stored.getPaymentId()).isEqualTo(paymentId);
            assertThat(stored.getResponseStatus()).isEqualTo(201);
            assertThat(stored.getResponseContentType()).isEqualTo("application/json");
            assertThat(stored.getResponseBody()).isEqualTo(response);
            assertThat(stored.getCompletedAt()).isEqualTo(now);
            assertThat(stored.getExpiresAt()).isEqualTo(now.plus(Duration.ofHours(24)));
            assertThat(stored.getVersion()).isPositive();
        });
    }

    @Test
    void version_whenTwoWritersUseSameVersion_shouldRejectStaleUpdate() {
        Long id = insertPayment(501L, "PENDING");
        try (var first = entityManagerFactory.createEntityManager();
             var stale = entityManagerFactory.createEntityManager()) {
            first.getTransaction().begin();
            stale.getTransaction().begin();
            var firstPayment = first.find(PaymentJpaEntity.class, id);
            var stalePayment = stale.find(PaymentJpaEntity.class, id);
            firstPayment.updateState(PaymentStatus.SUCCESS, null, databaseNow());
            first.getTransaction().commit();
            stalePayment.updateState(PaymentStatus.FAILED, null, databaseNow());
            try {
                assertThatThrownBy(stale::flush).isInstanceOf(OptimisticLockException.class);
            } finally {
                stale.getTransaction().rollback();
            }
        }
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, id))
                .isEqualTo("SUCCESS");
    }

    @Test
    void mapping_whenStateIsNotChanged_shouldLeaveVersionAndTimestampUntouched() {
        Long id = insertPayment(501L, "SUCCESS");
        Instant original = jdbc.queryForObject("SELECT updated_at FROM payments WHERE id = ?",
                (row, index) -> row.getTimestamp(1).toInstant(), id);
        transactions().executeWithoutResult(tx -> {
            var payment = entityManager.find(PaymentJpaEntity.class, id);
            payment.updateState(payment.getStatus(), payment.getTransactionReference(), payment.getUpdatedAt());
            entityManager.flush();
            assertThat(payment.getVersion()).isZero();
            assertThat(payment.getUpdatedAt()).isEqualTo(original);
        });
    }

    @Test
    void attemptPolicy_whenPreviousAttemptsFailed_shouldAllowNewPending() {
        insertPayment(501L, "FAILED");
        insertPayment(501L, "FAILED");
        insertPayment(501L, "PENDING");
        assertInvalidPaymentStatus(501L, "PENDING");
        assertInvalidPaymentStatus(501L, "SUCCESS");
        insertPayment(502L, "PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = 501", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void attemptPolicy_whenPaymentSucceeded_shouldRejectNewActiveAttempt() {
        insertPayment(501L, "FAILED");
        insertPayment(501L, "SUCCESS");
        assertInvalidPaymentStatus(501L, "PENDING");
        assertInvalidPaymentStatus(501L, "SUCCESS");
    }

    @Test
    void attemptPolicy_whenPendingBecomesFailed_shouldReleaseUniqueSlot() {
        Long id = insertPayment(501L, "PENDING");
        jdbc.update("UPDATE payments SET status = 'FAILED' WHERE id = ?", id);
        insertPayment(501L, "PENDING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.001", "NaN", "Infinity", "-Infinity"})
    @NullSource
    void amount_whenInvalid_shouldBeRejectedByDatabase(String amount) {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payments (order_id, method, status, amount) VALUES (501, 'COD', 'PENDING', ?::numeric)
                """, amount)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiredValuesAndEnums_whenInvalid_shouldBeRejectedByDatabase() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payments (method, status, amount) VALUES ('COD', 'PENDING', 1)
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payments (order_id, method, status, amount) VALUES (501, 'CARD', 'PENDING', 1)
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertInvalidPaymentStatus(501L, "PROCESSING");
        assertInvalidPaymentStatus(501L, null);
    }

    @Test
    void idempotencyKey_whenSameOrDifferentCase_shouldEnforceExactEquality() {
        insertClaim("Key-1", 501L);
        assertThatThrownBy(() -> insertClaim("Key-1", 502L))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertClaim("key-1", 501L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_idempotency", Integer.class)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " key", "key ", "key/key", "kunci-é", "key\n", "_key"})
    void idempotencyKey_whenInvalid_shouldBeRejectedByDatabase(String key) {
        assertThatThrownBy(() -> insertClaim(key, 501L)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotencyKey_whenAtLengthBoundary_shouldAccept128AndReject129() {
        insertClaim("a".repeat(128), 501L);
        assertThatThrownBy(() -> insertClaim("a".repeat(129), 501L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void processing_whenMissingOwnerLeaseOrFingerprint_shouldRejectIncompleteClaim() {
        insertClaim("claim", 501L);
        for (String assignment : new String[]{"owner_token = NULL", "owner_token = ''", "lease_until = NULL",
                "fingerprint = 'invalid'", "state = 'FAILED'", "response_status = 201"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE payment_idempotency SET " + assignment))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void completed_whenResponseOrRetentionMissing_shouldRejectIncompleteSnapshot() {
        insertClaim("completed", 501L);
        Long id = insertPayment(501L, "PENDING");
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_idempotency SET state = 'COMPLETED'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("""
                UPDATE payment_idempotency SET state = 'COMPLETED', payment_id = ?, response_status = 201,
                    response_content_type = 'application/json', response_body = '{"status":"PENDING"}',
                    completed_at = CURRENT_TIMESTAMP, expires_at = CURRENT_TIMESTAMP + INTERVAL '24 hours'
                """, id);
        for (String assignment : new String[]{"payment_id = NULL", "payment_id = -999", "response_status = NULL",
                "response_status = 500", "response_content_type = NULL", "response_content_type = 'text/html'",
                "response_body = NULL", "response_body = ''", "completed_at = NULL", "expires_at = NULL",
                "expires_at = completed_at + INTERVAL '1 hour'", "state = 'PROCESSING'"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE payment_idempotency SET " + assignment))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("DELETE FROM payments WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transaction_whenFinalizationRollsBack_shouldKeepOnlyOriginalProcessingClaim() {
        insertClaim("rollback", 501L);
        transactions().executeWithoutResult(tx -> {
            Long id = insertPayment(501L, "PENDING");
            jdbc.update("""
                    UPDATE payment_idempotency SET state = 'COMPLETED', payment_id = ?, response_status = 201,
                        response_content_type = 'application/json', response_body = '{}',
                        completed_at = CURRENT_TIMESTAMP, expires_at = CURRENT_TIMESTAMP + INTERVAL '24 hours'
                    """, id);
            tx.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM payment_idempotency", String.class)).isEqualTo("PROCESSING");
    }

    @Test
    void uniqueIndexes_whenInsertIsUncommitted_shouldBlockDuplicateThenRejectAfterCommit() throws Exception {
        try (var first = DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
             var second = DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
             var firstStatement = first.createStatement();
             var secondStatement = second.createStatement()) {
            first.setAutoCommit(false);
            secondStatement.execute("SET lock_timeout = '250ms'");
            for (String insert : new String[]{
                    "INSERT INTO payments (order_id, method, status, amount) VALUES (501, 'COD', 'PENDING', 0)",
                    """
                    INSERT INTO payment_idempotency
                        (operation, key, order_id, method, fingerprint, state, owner_token, lease_until)
                    VALUES ('CREATE_PAYMENT_V1', 'concurrent', 501, 'COD',
                        repeat('a', 64), 'PROCESSING', 'owner', clock_timestamp() + INTERVAL '30 seconds')
                    """}) {
                firstStatement.executeUpdate(insert);
                assertThatThrownBy(() -> secondStatement.executeUpdate(insert))
                        .isInstanceOfSatisfying(SQLException.class,
                                error -> assertThat(error.getSQLState()).isEqualTo("55P03"));
                first.commit();
                assertThatThrownBy(() -> secondStatement.executeUpdate(insert))
                        .isInstanceOfSatisfying(SQLException.class,
                                error -> assertThat(error.getSQLState()).isEqualTo("23505"));
            }
        }
    }

    private TransactionTemplate transactions() { return new TransactionTemplate(transactionManager); }

    private Instant databaseNow() {
        return jdbc.queryForObject("SELECT clock_timestamp()", (row, index) -> row.getTimestamp(1).toInstant());
    }

    private Long insertPayment(Long orderId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO payments (order_id, method, status, amount) VALUES (?, 'COD', ?, 0) RETURNING id
                """, Long.class, orderId, status);
    }

    private void assertInvalidPaymentStatus(Long orderId, String status) {
        assertThatThrownBy(() -> insertPayment(orderId, status)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertClaim(String key, Long orderId) {
        jdbc.update("""
                INSERT INTO payment_idempotency
                    (operation, key, order_id, method, fingerprint, state, owner_token, lease_until)
                VALUES ('CREATE_PAYMENT_V1', ?, ?, 'COD', ?, 'PROCESSING', 'owner-1', clock_timestamp() + INTERVAL '30 seconds')
                """, key, orderId, "a".repeat(64));
    }
}
