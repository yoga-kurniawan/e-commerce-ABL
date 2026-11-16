package id.ac.ub.abl.order.adapter.outbound.database;

import java.util.List;

import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdersRepository extends JpaRepository<OrderJpaEntity, Long> {

    List<OrderJpaEntity> findByCustomerIdOrderByIdDesc(Long customerId);
}
