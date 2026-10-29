package id.ac.ub.abl.payment.adapter.outbound.database;

import java.util.Optional;

import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentIdempotencyId;
import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentIdempotencyJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Query persistence saja. Caller membandingkan payload, expiry dan token di bawah lock. */
public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotencyJpaEntity, PaymentIdempotencyId> {
    /** findById biasa juga dipakai inspect/reconciliation; error database tidak diubah menjadi Optional.empty(). */
    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PaymentIdempotencyJpaEntity i where i.id = :id")
    Optional<PaymentIdempotencyJpaEntity> findByIdForUpdate(@Param("id") PaymentIdempotencyId id);

    /** 1 = insert baru, 0 = key sudah ada. Belum menentukan replay/mismatch/takeover. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = """
            INSERT INTO payment_idempotency (operation, key, order_id, method, fingerprint, state, owner_token, lease_until)
            VALUES (:operation, :key, :orderId, :method, :fingerprint, 'PROCESSING', :ownerToken,
                clock_timestamp() + (:leaseMillis * INTERVAL '1 millisecond'))
            ON CONFLICT (operation, key) DO NOTHING
            """, nativeQuery = true)
    int insertProcessingIfAbsent(@Param("operation") String operation, @Param("key") String key,
            @Param("orderId") Long orderId, @Param("method") String method,
            @Param("fingerprint") String fingerprint, @Param("ownerToken") String ownerToken,
            @Param("leaseMillis") long leaseMillis);

    /** Hanya setelah rollback finalisasi dipastikan; token lama tidak dapat menghapus pemilik baru. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = """
            DELETE FROM payment_idempotency WHERE operation = :operation AND key = :key
                AND state = 'PROCESSING' AND owner_token = :ownerToken
            """, nativeQuery = true)
    int deleteProcessingOwnedBy(@Param("operation") String operation, @Param("key") String key,
            @Param("ownerToken") String ownerToken);

    /** Penggantian key expired harus dalam transaksi yang sama dengan lock row dan insert baru. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = """
            DELETE FROM payment_idempotency WHERE operation = :operation AND key = :key
                AND state = 'COMPLETED' AND expires_at <= statement_timestamp()
            """, nativeQuery = true)
    int deleteExpiredCompletedByKey(@Param("operation") String operation, @Param("key") String key);

    /** Batch cleanup tidak menghapus PROCESSING; row yang sedang dikunci dilewati. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = """
            WITH expired AS (
                SELECT operation, key FROM payment_idempotency
                WHERE state = 'COMPLETED' AND expires_at <= statement_timestamp()
                ORDER BY expires_at, operation, key LIMIT :batchSize FOR UPDATE SKIP LOCKED
            )
            DELETE FROM payment_idempotency i USING expired e
            WHERE i.operation = e.operation AND i.key = e.key
            """, nativeQuery = true)
    int deleteExpiredCompletedBatch(@Param("batchSize") int batchSize);
}
