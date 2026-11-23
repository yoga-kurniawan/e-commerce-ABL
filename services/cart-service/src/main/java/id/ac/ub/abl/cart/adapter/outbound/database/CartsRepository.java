package id.ac.ub.abl.cart.adapter.outbound.database;

import java.util.Optional;

import id.ac.ub.abl.cart.adapter.outbound.database.entity.CartJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Akses Spring Data untuk entity lokal Cart; use case tetap memakai CartStore. */
public interface CartsRepository extends JpaRepository<CartJpaEntity, Long> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<CartJpaEntity> findById(Long id);

    /**
     * Memaksa increment @Version pada parent Cart meskipun hanya child cart_items yang berubah.
     * Tidak memakai @EntityGraph di sini agar Hibernate 7 tidak menerapkan lock OPTIMISTIC_FORCE_INCREMENT
     * pada CartItemJpaEntity yang tidak memiliki kolom @Version; items dimuat LAZY dalam transaksi caller.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select c from CartJpaEntity c where c.id = :id")
    Optional<CartJpaEntity> findByIdForOptimisticUpdate(@Param("id") Long id);
}
