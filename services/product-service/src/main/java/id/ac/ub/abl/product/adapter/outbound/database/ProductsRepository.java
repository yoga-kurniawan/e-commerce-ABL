package id.ac.ub.abl.product.adapter.outbound.database;

import java.util.List;
import java.util.Optional;

import id.ac.ub.abl.product.adapter.outbound.database.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Akses Spring Data untuk entity lokal; use case tetap memakai ProductStore. */
public interface ProductsRepository extends JpaRepository<ProductJpaEntity, Long> {
    Optional<ProductJpaEntity> findByIdAndActiveTrue(Long productId);

    List<ProductJpaEntity> findAllByActiveTrue();
}
