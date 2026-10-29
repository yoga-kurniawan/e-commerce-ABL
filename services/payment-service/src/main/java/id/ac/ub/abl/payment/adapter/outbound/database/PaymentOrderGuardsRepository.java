package id.ac.ub.abl.payment.adapter.outbound.database;

import java.util.Optional;

import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentOrderGuardJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentOrderGuardsRepository extends JpaRepository<PaymentOrderGuardJpaEntity, Long> {
    /** ON CONFLICT menghindari transaksi aborted ketika guard sudah tersedia. Tetap dapat menunggu lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = "INSERT INTO payment_order_guards (order_id) VALUES (:orderId) ON CONFLICT (order_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("orderId") Long orderId);

    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from PaymentOrderGuardJpaEntity g where g.orderId = :orderId")
    Optional<PaymentOrderGuardJpaEntity> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
