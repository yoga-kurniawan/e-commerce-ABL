package id.ac.ub.abl.order.adapter.outbound.database;

import java.util.List;
import java.util.Optional;

import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdersRepository extends JpaRepository<OrderJpaEntity, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<OrderJpaEntity> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    List<OrderJpaEntity> findByCustomerIdOrderByIdDesc(Long customerId);
}
