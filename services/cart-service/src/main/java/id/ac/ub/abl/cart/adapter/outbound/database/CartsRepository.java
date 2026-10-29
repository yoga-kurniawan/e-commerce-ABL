package id.ac.ub.abl.cart.adapter.outbound.database;

import id.ac.ub.abl.cart.adapter.outbound.database.entity.CartJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Akses Spring Data untuk entity lokal Cart; use case tetap memakai CartStore. */
public interface CartsRepository extends JpaRepository<CartJpaEntity, Long> {
}
