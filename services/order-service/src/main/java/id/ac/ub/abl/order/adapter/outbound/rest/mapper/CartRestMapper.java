package id.ac.ub.abl.order.adapter.outbound.rest.mapper;

import java.util.List;

import id.ac.ub.abl.order.adapter.outbound.rest.dto.CartResponse;
import id.ac.ub.abl.order.domain.CartItemSnapshot;
import id.ac.ub.abl.order.domain.CartSnapshot;
import org.springframework.stereotype.Component;

@Component
public class CartRestMapper {
    public CartSnapshot toSnapshot(CartResponse response) {
        List<CartItemSnapshot> items = response.items() == null ? List.of() : response.items().stream()
                .map(item -> new CartItemSnapshot(item.productId(), item.quantity(), item.subtotal()))
                .toList();
        return new CartSnapshot(response.id(), response.customerId(), items);
    }
}
