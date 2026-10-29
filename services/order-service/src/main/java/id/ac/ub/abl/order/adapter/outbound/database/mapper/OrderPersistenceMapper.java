package id.ac.ub.abl.order.adapter.outbound.database.mapper;

import java.util.List;

import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderItemJpaEntity;
import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderJpaEntity;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.domain.OrderItem;
import org.springframework.stereotype.Component;

@Component
public class OrderPersistenceMapper {

    public OrderJpaEntity toEntity(Order domain) {
        OrderJpaEntity entity = new OrderJpaEntity(domain.getId(), domain.getCustomerId(), domain.getCartId(),
                domain.getStatus(), domain.getShippingAddress());
        for (OrderItem item : domain.getItems()) {
            entity.addItem(new OrderItemJpaEntity(entity, item.getProductId(), item.getProductName(),
                    item.getQuantity(), item.getUnitPrice()));
        }
        return entity;
    }

    /** Item snapshot tidak berubah setelah dibuat; hanya atribut pesanan yang diperbarui. */
    public void updateEntity(OrderJpaEntity entity, Order domain) {
        entity.setStatus(domain.getStatus());
        entity.setShippingAddress(domain.getShippingAddress());
    }

    public Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(item -> OrderItem.restore(item.getProductId(), item.getProductName(),
                        item.getQuantity(), item.getUnitPrice()))
                .toList();
        return Order.restore(entity.getId(), entity.getCustomerId(), entity.getCartId(),
                entity.getStatus(), entity.getShippingAddress(), items);
    }
}
