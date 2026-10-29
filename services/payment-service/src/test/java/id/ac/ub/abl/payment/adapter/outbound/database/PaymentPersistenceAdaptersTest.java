package id.ac.ub.abl.payment.adapter.outbound.database;

import java.math.BigDecimal;
import java.util.concurrent.*;
import id.ac.ub.abl.payment.domain.*;
import id.ac.ub.abl.payment.domain.exception.*;
import id.ac.ub.abl.payment.usecase.command.*;
import id.ac.ub.abl.payment.usecase.model.*;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import id.ac.ub.abl.payment.usecase.port.out.CreationResponseEncoder;
import id.ac.ub.abl.payment.adapter.outbound.database.mapper.PaymentPersistenceMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({PaymentDatabaseSession.class, PaymentTransactions.class, PaymentPersistenceMapper.class,
        PaymentPersistenceAdapter.class, PaymentIdempotencyAdapter.class, PaymentCreationPersistenceAdapter.class,
        PaymentPersistenceAdaptersTest.EncoderConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentPersistenceAdaptersTest {
    @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }
    @Autowired PaymentPersistenceAdapter payments;
    @Autowired PaymentIdempotencyAdapter keys;
    @Autowired PaymentCreationPersistenceAdapter creation;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestEncoder encoder;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired PaymentDatabaseSession session;
    @Autowired PaymentsRepository paymentsRepository;
    @Autowired PaymentOrderGuardsRepository guardsRepository;
    @Autowired PaymentIdempotencyRepository idempotencyRepository;
    @Autowired PaymentPersistenceMapper mapper;
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE payment_idempotency, payments, payment_order_guards");
        encoder.action = () -> { };
    }
    @Test void createAndReplay_whenStatusChanges_shouldPreserveInitialBodyAndExactAmount() {
        var request = request("replay", 501L);
        var result = creation.commit(claim(request), order(501L));
        assertThat(result.payment().getAmount()).isEqualTo(new BigDecimal("350000.12500"));
        assertThat(keys.claim(request)).isEqualTo(new IdempotencyClaimResult.Replayed(result.response()));
        payments.changeReference(new PatchPaymentCommand(result.payment().getId(), "  ref  "));
        payments.applyResult(new ApplyPaymentResultCommand(result.payment().getId(), PaymentStatus.SUCCESS));
        assertThat(keys.inspect(request)).isEqualTo(new IdempotencyLookupResult.Completed(result.response()));
        assertThat(keys.claim(request)).isEqualTo(new IdempotencyClaimResult.Replayed(result.response()));
        assertThat(payments.findById(result.payment().getId()).orElseThrow().getTransactionReference()).isEqualTo("  ref  ");
        assertThat(jdbc.queryForObject("SELECT expires_at - completed_at = INTERVAL '24 hours' FROM payment_idempotency", Boolean.class)).isTrue();
    }
    @Test void activeClaim_whenPayloadMatchesOrDiffers_shouldReturnDistinctOutcomes() {
        var request = request("key", 501L);
        claim(request);
        assertThat(keys.claim(request)).isInstanceOf(IdempotencyClaimResult.InProgress.class);
        assertThat(keys.claim(request("key", 502L))).isInstanceOf(IdempotencyClaimResult.PayloadMismatch.class);
        assertThat(keys.inspect(request("key", 502L))).isInstanceOf(IdempotencyLookupResult.PayloadMismatch.class);
        assertThat(keys.inspect(request("missing", 501L))).isInstanceOf(IdempotencyLookupResult.Missing.class);
    }
    @Test void rollback_whenEncoderFails_shouldLeaveNoPartialPaymentAndAllowExplicitRelease() {
        var request = request("rollback", 501L);
        var claim = claim(request);
        encoder.action = () -> { throw new IllegalStateException("encoder fault"); };
        assertThatThrownBy(() -> creation.commit(claim, order(501L))).hasMessage("encoder fault");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
        assertThat(keys.inspect(request)).isInstanceOf(IdempotencyLookupResult.Processing.class);
        assertThat(keys.releaseAfterConfirmedRollback(claim)).isTrue();
        encoder.action = () -> { };
        creation.commit(claim(request), order(501L));
        assertThat(keys.releaseAfterConfirmedRollback(claim)).isFalse();
    }
    @Test void takeover_whenLeaseExpired_shouldFenceOldOwnerAndKeepNewClaimOnOldRelease() {
        var request = request("takeover", 501L);
        var old = claim(request);
        jdbc.update("UPDATE payment_idempotency SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        var replacement = claim(request);
        assertThat(replacement.ownerToken()).isNotEqualTo(old.ownerToken());
        assertThatThrownBy(() -> creation.commit(old, order(501L))).satisfies(error -> code(error, "REQUEST_IN_PROGRESS"));
        assertThat(keys.releaseAfterConfirmedRollback(old)).isFalse();
        creation.commit(replacement, order(501L));
    }
    @Test void expiredLease_whenNotTakenOver_shouldStillRejectOldFinalization() {
        var claim = claim(request("expired", 501L));
        jdbc.update("UPDATE payment_idempotency SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        assertThatThrownBy(() -> creation.commit(claim, order(501L))).satisfies(error -> code(error, "REQUEST_IN_PROGRESS"));
    }
    @Test void retention_whenDatabaseTimeAged_shouldReplaceExpiredKeyAndNeverCleanProcessingOrPayment() {
        var request = request("old", 501L);
        var result = creation.commit(claim(request), order(501L));
        var expiry = jdbc.queryForObject("SELECT expires_at FROM payment_idempotency", java.sql.Timestamp.class);
        keys.claim(request);
        assertThat(jdbc.queryForObject("SELECT expires_at FROM payment_idempotency", java.sql.Timestamp.class)).isEqualTo(expiry);
        ageCompleted();
        assertThat(keys.inspect(request)).isInstanceOf(IdempotencyLookupResult.Missing.class);
        claim(request("old", 502L)); // key expired diperlakukan operasi baru, payload baru
        assertThat(keys.deleteExpiredCompleted(10)).isZero();
        assertThat(payments.findById(result.payment().getId())).isPresent();
        assertThat(keys.inspect(request("old", 502L))).isInstanceOf(IdempotencyLookupResult.Processing.class);
        var fresh = request("cleanup", 503L);
        creation.commit(claim(fresh), order(503L));
        ageCompleted();
        assertThat(keys.deleteExpiredCompleted(1)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(2);
        assertThat(keys.inspect(request("old", 502L))).isInstanceOf(IdempotencyLookupResult.Processing.class);
    }
    @Test void attempts_whenFailedOrSucceeded_shouldEnforcePolicyAndKeepNoOpVersion() {
        var first = creation.commit(claim(request("first", 501L)), order(501L)).payment();
        var second = claim(request("second", 501L));
        assertThatThrownBy(() -> creation.commit(second, order(501L))).satisfies(error -> code(error, "PAYMENT_ALREADY_PENDING"));
        payments.applyResult(new ApplyPaymentResultCommand(first.getId(), PaymentStatus.FAILED));
        var next = creation.commit(second, order(501L)).payment();
        payments.applyResult(new ApplyPaymentResultCommand(next.getId(), PaymentStatus.SUCCESS));
        var metadata = jdbc.queryForMap("SELECT version, created_at, updated_at FROM payments WHERE id = ?", next.getId());
        payments.applyResult(new ApplyPaymentResultCommand(next.getId(), PaymentStatus.SUCCESS));
        assertThat(jdbc.queryForMap("SELECT version, created_at, updated_at FROM payments WHERE id = ?", next.getId())).isEqualTo(metadata);
        assertThatThrownBy(() -> payments.changeReference(new PatchPaymentCommand(next.getId(), "ref"))).isInstanceOf(PaymentNotModifiableException.class);
        assertThatThrownBy(() -> payments.applyResult(new ApplyPaymentResultCommand(next.getId(), PaymentStatus.FAILED))).isInstanceOf(PaymentResultConflictException.class);
        assertThatThrownBy(() -> creation.commit(claim(request("third", 501L)), order(501L))).satisfies(error -> code(error, "ORDER_ALREADY_PAID"));
        assertThat(payments.findById(-1L)).isEmpty();
    }
    @Test void snapshot_whenMismatchedOrNotPayable_shouldRejectWithoutPayment() {
        var claim = claim(request("order", 501L));
        assertThatThrownBy(() -> creation.commit(claim, order(502L))).satisfies(error -> code(error, "ORDER_UPSTREAM_ERROR"));
        assertThatThrownBy(() -> creation.commit(claim, new OrderSnapshot(501L, BigDecimal.ONE, OrderSnapshot.Status.CANCELLED)))
                .isInstanceOf(OrderNotPayableException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
    }
    @Test void sameKey_whenClaimedConcurrently_shouldHaveExactlyOneOwner() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<IdempotencyClaimResult> job = () -> { await(start); return keys.claim(request("race", 501L)); };
            var first = pool.submit(job); var second = pool.submit(job); start.countDown();
            var results = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(IdempotencyClaimResult.Claimed.class::isInstance).count()).isEqualTo(1);
            assertThat(results.stream().filter(IdempotencyClaimResult.InProgress.class::isInstance).count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void differentKeys_whenFinalizedConcurrently_shouldCreateOneAttempt() throws Exception {
        var first = claim(request("a", 501L)); var second = claim(request("b", 501L));
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> a = () -> { await(start); try { return creation.commit(first, order(501L)); } catch (PaymentOperationException e) { return e; } };
            Callable<Object> b = () -> { await(start); try { return creation.commit(second, order(501L)); } catch (PaymentOperationException e) { return e; } };
            var x = pool.submit(a); var y = pool.submit(b); start.countDown();
            var results = java.util.List.of(x.get(10, TimeUnit.SECONDS), y.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(CreatePaymentResult.Created.class::isInstance).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void finalization_whenLeaseExpiresWhileRowLocked_shouldPreventTakeoverUntilCommit() throws Exception {
        var request = request("held", 501L); var claim = claim(request);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        encoder.action = () -> {
            jdbc.update("UPDATE payment_idempotency SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'");
            entered.countDown(); await(release);
        };
        var pool = Executors.newSingleThreadExecutor();
        try {
            var finalization = pool.submit(() -> creation.commit(claim, order(501L)));
            await(entered);
            assertThat(keys.claim(request)).isInstanceOf(IdempotencyClaimResult.InProgress.class);
            release.countDown();
            var result = finalization.get(10, TimeUnit.SECONDS);
            assertThat(keys.inspect(request)).isEqualTo(new IdempotencyLookupResult.Completed(result.response()));
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    private void ageCompleted() {
        jdbc.update("UPDATE payment_idempotency SET completed_at = CURRENT_TIMESTAMP - INTERVAL '25 hours', expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE state = 'COMPLETED'");
    }
    @Test void mutations_whenGuardBusy_shouldFailWithoutChangingPayment() throws Exception {
        var payment = creation.commit(claim(request("busy", 501L)), order(501L)).payment();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var holder = pool.submit(() -> new PaymentTransactions(transactionManager, session, 250, 30000).run("PAYMENT_OPERATION_BUSY", () -> {
                guardsRepository.findByOrderIdForUpdate(501L).orElseThrow();
                entered.countDown(); await(release); return true;
            }));
            await(entered);
            assertThatThrownBy(() -> payments.changeReference(new PatchPaymentCommand(payment.getId(), "ref")))
                    .satisfies(error -> code(error, "PAYMENT_OPERATION_BUSY"));
            assertThatThrownBy(() -> payments.applyResult(new ApplyPaymentResultCommand(payment.getId(), PaymentStatus.SUCCESS)))
                    .satisfies(error -> code(error, "PAYMENT_OPERATION_BUSY"));
            release.countDown(); holder.get(10, TimeUnit.SECONDS);
            assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payments.findById(payment.getId()).orElseThrow().getTransactionReference()).isNull();
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void terminalResults_whenRacing_shouldCommitOnceAndRejectOppositeResult() throws Exception {
        var payment = creation.commit(claim(request("results", 501L)), order(501L)).payment();
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> success = () -> { await(start); try { return payments.applyResult(new ApplyPaymentResultCommand(payment.getId(), PaymentStatus.SUCCESS)); } catch (RuntimeException e) { return e; } };
            Callable<Object> failed = () -> { await(start); try { return payments.applyResult(new ApplyPaymentResultCommand(payment.getId(), PaymentStatus.FAILED)); } catch (RuntimeException e) { return e; } };
            var x = pool.submit(success); var y = pool.submit(failed); start.countDown();
            var results = java.util.List.of(x.get(10, TimeUnit.SECONDS), y.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(Payment.class::isInstance).count()).isEqualTo(1);
            assertThat(results.stream().filter(PaymentResultConflictException.class::isInstance).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT version FROM payments WHERE id = ?", Long.class, payment.getId())).isEqualTo(1L);
        } finally { pool.shutdownNow(); }
    }
    @Test void commitAcknowledgement_whenLostAfterRealCommit_shouldReconcileWithoutDeletingResult() {
        var request = request("unknown-commit", 501L);
        var claim = claim(request);
        var faultyManager = new org.springframework.transaction.PlatformTransactionManager() {
            public org.springframework.transaction.TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {
                return transactionManager.getTransaction(definition);
            }
            public void commit(org.springframework.transaction.TransactionStatus status) {
                transactionManager.commit(status);
                throw new org.springframework.transaction.TransactionSystemException("injected lost commit acknowledgement");
            }
            public void rollback(org.springframework.transaction.TransactionStatus status) { transactionManager.rollback(status); }
        };
        var faultyTransactions = new PaymentTransactions(faultyManager, session, 250, 30000);
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        beans.addBean("encoder", encoder);
        var faultyCreation = new PaymentCreationPersistenceAdapter(paymentsRepository, guardsRepository, idempotencyRepository,
                mapper, session, faultyTransactions, beans.getBeanProvider(CreationResponseEncoder.class));
        assertThatThrownBy(() -> faultyCreation.commit(claim, order(501L)))
                .satisfies(error -> code(error, "DEPENDENCY_UNAVAILABLE"));
        assertThat(keys.inspect(request)).isInstanceOf(IdempotencyLookupResult.Completed.class);
        assertThat(keys.claim(request)).isInstanceOf(IdempotencyClaimResult.Replayed.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
    }
    private IdempotencyClaim claim(CreatePaymentCommand request) { return ((IdempotencyClaimResult.Claimed) keys.claim(request)).claim(); }
    private CreatePaymentCommand request(String key, Long order) { return new CreatePaymentCommand(order, PaymentMethod.COD, new IdempotencyKey(key)); }
    private OrderSnapshot order(Long id) { return new OrderSnapshot(id, new BigDecimal("350000.12500"), OrderSnapshot.Status.PENDING); }
    private void code(Throwable error, String expected) { assertThat(error).isInstanceOfSatisfying(PaymentOperationException.class, failure -> assertThat(failure.getCode()).isEqualTo(expected)); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Synchronization timeout"); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    @TestConfiguration(proxyBeanMethods = false) static class EncoderConfiguration {
        @Bean TestEncoder encoder() { return new TestEncoder(); }
    }
    static class TestEncoder implements CreationResponseEncoder {
        volatile Runnable action = () -> { };
        public StoredCreationResponse encode(Payment payment) {
            action.run();
            return new StoredCreationResponse(201, "application/json", "{\"id\":" + payment.getId() + ",\"status\":\"PENDING\"}");
        }
    }
}
