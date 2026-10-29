package id.ac.ub.abl.payment.adapter.outbound.database;

import java.time.Duration;
import java.sql.SQLException;
import java.util.function.Supplier;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Exception ditangani setelah boundary commit/rollback, bukan di dalam transaksi SQL aborted. */
@Component
public class PaymentTransactions {
    private final TransactionTemplate transactions;
    private final PaymentDatabaseSession session;
    private final Duration lockBudget;
    private final Duration lease;
    public PaymentTransactions(PlatformTransactionManager manager, PaymentDatabaseSession session,
            @Value("${payment.persistence.lock-timeout-ms:250}") long lockMillis,
            @Value("${payment.persistence.claim-lease-ms:30000}") long leaseMillis) {
        if (lockMillis < 1 || leaseMillis < 1) { throw new IllegalArgumentException("Durasi persistence harus positif"); }
        this.session = session;
        this.lockBudget = Duration.ofMillis(lockMillis);
        this.lease = Duration.ofMillis(leaseMillis);
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Duration lease() { return lease; }
    public <T> T claimLock(Supplier<T> action) {
        try { return action.get(); }
        catch (DataAccessException failure) {
            if (isLockTimeout(failure)) {
                throw new PaymentOperationException("REQUEST_IN_PROGRESS", "Klaim sedang dikunci", failure);
            }
            throw failure;
        }
    }
    private boolean isLockTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) { return true; }
        }
        return false;
    }
    public <T> T run(String busyCode, Supplier<T> action) {
        try {
            return transactions.execute(tx -> {
                session.configureLockTimeout(lockBudget);
                return action.get();
            });
        } catch (DataAccessException | TransactionException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                    throw new PaymentOperationException(busyCode, "Operasi pembayaran sedang diproses", failure);
                }
            }
            // Termasuk hasil commit ambigu. Tidak pernah menghapus klaim atau menyatakan Payment FAILED.
            throw new PaymentOperationException("DEPENDENCY_UNAVAILABLE", "Hasil database perlu diperiksa kembali menggunakan key yang sama", failure);
        }
    }
}
