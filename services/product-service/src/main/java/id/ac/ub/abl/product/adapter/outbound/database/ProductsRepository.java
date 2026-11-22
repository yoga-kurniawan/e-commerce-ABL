package id.ac.ub.abl.product.adapter.outbound.database;

import java.util.List;
import java.util.Optional;

import id.ac.ub.abl.product.adapter.outbound.database.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Akses Spring Data untuk entity lokal; use case tetap memakai ProductStore. */
public interface ProductsRepository extends JpaRepository<ProductJpaEntity, Long> {
    Optional<ProductJpaEntity> findByIdAndActiveTrue(Long productId);

    List<ProductJpaEntity> findAllByActiveTrueOrderByIdAsc();

    // POSITION treats %, _ and backslash literally, unlike a LIKE pattern.
    // PostgreSQL LIMIT NULL returns every row; long OFFSET avoids page*size overflow.
    @Query(value = """
            SELECT * FROM products WHERE active = true
              AND position(lower(:name) in lower(name)) > 0
            ORDER BY id ASC LIMIT CAST(:limit AS integer) OFFSET :offset
            """, nativeQuery = true)
    List<ProductJpaEntity> searchActive(@Param("name") String name,
                                        @Param("limit") Integer limit, @Param("offset") long offset);

    @Query(value = """
            SELECT count(*) FROM products WHERE active = true
              AND position(lower(:name) in lower(name)) > 0
            """, nativeQuery = true)
    long countMatchingActive(@Param("name") String name);
}
