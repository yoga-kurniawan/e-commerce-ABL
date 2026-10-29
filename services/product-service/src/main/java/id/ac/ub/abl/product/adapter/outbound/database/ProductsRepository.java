package id.ac.ub.abl.product.adapter.outbound.database;

import id.ac.ub.abl.product.adapter.outbound.database.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Akses Spring Data untuk entity lokal; use case tetap memakai ProductStore. */
public interface ProductsRepository extends JpaRepository<ProductJpaEntity, Long> {
}
