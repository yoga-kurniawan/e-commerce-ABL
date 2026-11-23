package id.ac.ub.abl.payment.adapter.outbound.database;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentJpaEntity;
import id.ac.ub.abl.payment.adapter.outbound.database.mapper.PaymentPersistenceMapper;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.domain.PaymentMethod;
import id.ac.ub.abl.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentReadOnlyTransactionTest {

    private PaymentsRepository paymentsRepository;
    private PaymentOrderGuardsRepository guardsRepository;
    private PaymentPersistenceMapper mapper;
    private PaymentDatabaseSession session;
    private PaymentTransactions transactions;
    private PaymentPersistenceAdapter adapter;
    private StubTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        paymentsRepository = mock(PaymentsRepository.class);
        guardsRepository = mock(PaymentOrderGuardsRepository.class);
        mapper = mock(PaymentPersistenceMapper.class);
        session = mock(PaymentDatabaseSession.class);
        transactionManager = new StubTransactionManager();
        transactions = new PaymentTransactions(transactionManager, session, 250, 30000);
        adapter = new PaymentPersistenceAdapter(paymentsRepository, guardsRepository, mapper, session, transactions);
    }

    @Test
    @DisplayName("findById runs inside a read-only transaction and never invokes configureLockTimeout")
    void findById_runsInReadOnlyTransactionWithoutLockTimeout() {
        AtomicBoolean wasReadOnly = new AtomicBoolean(false);
        PaymentJpaEntity entity = mock(PaymentJpaEntity.class);
        Payment domainPayment = Payment.restore(
                10L, 100L, PaymentMethod.TRANSFER, new BigDecimal("50000"),
                PaymentStatus.PENDING, null);

        when(paymentsRepository.findById(10L)).thenAnswer(inv -> {
            wasReadOnly.set(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.of(entity);
        });
        when(mapper.toDomain(entity)).thenReturn(domainPayment);

        Optional<Payment> result = adapter.findById(10L);

        assertThat(result).contains(domainPayment);
        assertThat(wasReadOnly.get()).isTrue();
        verify(session, never()).configureLockTimeout(any());
        verify(paymentsRepository).findById(10L);
    }

    @Test
    @DisplayName("findByOrderId runs inside a read-only transaction and never invokes configureLockTimeout")
    void findByOrderId_runsInReadOnlyTransactionWithoutLockTimeout() {
        AtomicBoolean wasReadOnly = new AtomicBoolean(false);
        PaymentJpaEntity entity = mock(PaymentJpaEntity.class);
        Payment domainPayment = Payment.restore(
                10L, 100L, PaymentMethod.TRANSFER, new BigDecimal("50000"),
                PaymentStatus.PENDING, null);

        when(paymentsRepository.findByOrderIdOrderByIdAsc(100L)).thenAnswer(inv -> {
            wasReadOnly.set(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return List.of(entity);
        });
        when(mapper.toDomain(entity)).thenReturn(domainPayment);

        List<Payment> result = adapter.findByOrderId(100L);

        assertThat(result).containsExactly(domainPayment);
        assertThat(wasReadOnly.get()).isTrue();
        verify(session, never()).configureLockTimeout(any());
        verify(paymentsRepository).findByOrderIdOrderByIdAsc(100L);
    }

    @Test
    @DisplayName("run uses write transaction and invokes configureLockTimeout")
    void run_invokesConfigureLockTimeout() {
        AtomicBoolean wasReadOnly = new AtomicBoolean(true);

        String outcome = transactions.run("BUSY", () -> {
            wasReadOnly.set(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return "ok";
        });

        assertThat(outcome).isEqualTo("ok");
        assertThat(wasReadOnly.get()).isFalse();
        verify(session, times(1)).configureLockTimeout(any());
    }

    @Test
    @DisplayName("runReadOnly uses default 3-second timeout and supports custom readTimeoutSeconds override")
    void runReadOnly_configuresDefaultAndCustomTimeout() {
        transactions.runReadOnly("BUSY", () -> "ok");
        assertThat(transactionManager.lastTimeout).isEqualTo(3);

        PaymentTransactions customTransactions =
                new PaymentTransactions(transactionManager, session, 250, 30000, 7);
        customTransactions.runReadOnly("BUSY", () -> "ok");
        assertThat(transactionManager.lastTimeout).isEqualTo(7);
    }

    @Test
    @DisplayName("constructor rejects non-positive readTimeoutSeconds")
    void constructor_rejectsNonPositiveReadTimeoutSeconds() {
        assertThatThrownBy(() -> new PaymentTransactions(transactionManager, session, 250, 30000, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentTransactions(transactionManager, session, 250, 30000, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Stub PlatformTransactionManager that properly binds read-only status
     * to TransactionSynchronizationManager to allow assertions.
     */
    static class StubTransactionManager implements PlatformTransactionManager {
        int lastTimeout = TransactionDefinition.TIMEOUT_DEFAULT;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            lastTimeout = definition.getTimeout();
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(definition.isReadOnly());
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
            TransactionSynchronizationManager.clear();
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
            TransactionSynchronizationManager.clear();
        }
    }
}
