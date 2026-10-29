package id.ac.ub.abl.order.adapter.outbound.database.mapper;

import java.math.BigDecimal;
import java.util.List;

import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderJpaEntity;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.domain.OrderItem;
import id.ac.ub.abl.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderPersistenceMapperTest {
    private final OrderPersistenceMapper mapper = new OrderPersistenceMapper();

    private Order sampleOrder() {
        return Order.restore(501L, 101L, 10L, OrderStatus.PENDING, "Jl. Veteran", List.of(
                OrderItem.create(1L, "Keyboard", 2, new BigDecimal("350000.125")),
                OrderItem.create(2L, "Mouse", 1, new BigDecimal("150000"))));
    }

    @Test
    void roundTripPreservesOrderAndItems() {
        Order original = sampleOrder();

        OrderJpaEntity entity = mapper.toEntity(original);
        Order restored = mapper.toDomain(entity);

        assertEquals(501L, entity.getId());
        assertSame(entity, entity.getItems().get(0).getOrder());
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getCustomerId(), restored.getCustomerId());
        assertEquals(original.getCartId(), restored.getCartId());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getShippingAddress(), restored.getShippingAddress());
        assertEquals(original.getItems(), restored.getItems());
        assertEquals(0, original.getTotal().compareTo(restored.getTotal()));
    }

    @Test
    void updateEntityChangesOnlyOrderAttributes() {
        OrderJpaEntity entity = mapper.toEntity(sampleOrder());
        Order changed = sampleOrder().changeShippingAddress("Jl. Baru");

        mapper.updateEntity(entity, changed);

        assertEquals("Jl. Baru", entity.getShippingAddress());
        assertEquals(OrderStatus.PENDING, entity.getStatus());
        assertEquals(2, entity.getItems().size());
    }
}
