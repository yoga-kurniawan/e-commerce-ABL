package id.ac.ub.abl.payment.adapter.outbound.database;

import java.util.Optional;

import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentJpaEntity;
import id.ac.ub.abl.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentsRepository extends JpaRepository<PaymentJpaEntity, Long> {
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    /** Partial unique index membatasi hasil menjadi maksimum satu baris. Caller memegang guard Order. */
    @Query("select p from PaymentJpaEntity p where p.orderId = :orderId and p.status in (" +
            "id.ac.ub.abl.payment.domain.PaymentStatus.PENDING, id.ac.ub.abl.payment.domain.PaymentStatus.SUCCESS)")
    Optional<PaymentJpaEntity> findBlockingAttempt(@Param("orderId") Long orderId);

    /** Lookup orderId sebelum lock guard; bukan lock/update Payment. */
    @Query("select p.orderId from PaymentJpaEntity p where p.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);

    /** Transaksi caller wajib: configure lock budget, lock guard, baru Payment. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentJpaEntity p where p.id = :id")
    Optional<PaymentJpaEntity> findByIdForUpdate(@Param("id") Long id);
}
