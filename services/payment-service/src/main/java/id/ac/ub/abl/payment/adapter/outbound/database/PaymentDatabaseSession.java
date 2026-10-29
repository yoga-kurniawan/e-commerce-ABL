package id.ac.ub.abl.payment.adapter.outbound.database;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Operasi PostgreSQL pada koneksi/transaksi yang sama dengan repository JPA. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentDatabaseSession {
    private final EntityManager entityManager;

    public PaymentDatabaseSession(EntityManager entityManager) { this.entityManager = entityManager; }

    /** Dipanggil sebelum insert/lock. SET LOCAL berakhir saat commit/rollback; tidak bocor ke pool. */
    public void configureLockTimeout(Duration budget) {
        if (budget == null || budget.isNegative() || budget.isZero() || budget.toMillis() < 1) {
            throw new IllegalArgumentException("Lock budget minimal 1 ms; nilai 0 menonaktifkan timeout PostgreSQL");
        }
        entityManager.createNativeQuery("select set_config('lock_timeout', :budget, true)")
                .setParameter("budget", budget.toMillis() + "ms").getSingleResult();
    }

    public Instant now() {
        return (Instant) entityManager.createNativeQuery("select clock_timestamp()", Instant.class).getSingleResult();
    }
}
