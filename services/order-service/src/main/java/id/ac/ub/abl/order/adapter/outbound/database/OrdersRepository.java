package id.ac.ub.abl.order.adapter.outbound.database;

import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Akses Spring Data untuk entity lokal Order; use case tetap memakai OrderStore. */
public interface OrdersRepository extends JpaRepository<OrderJpaEntity, Long> {
}
