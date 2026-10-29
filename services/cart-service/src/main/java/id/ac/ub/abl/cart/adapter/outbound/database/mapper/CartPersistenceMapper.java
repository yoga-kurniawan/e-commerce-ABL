package id.ac.ub.abl.cart.adapter.outbound.database.mapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import id.ac.ub.abl.cart.adapter.outbound.database.entity.CartItemJpaEntity;
import id.ac.ub.abl.cart.adapter.outbound.database.entity.CartJpaEntity;
import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.CartItem;
import org.springframework.stereotype.Component;

@Component
public class CartPersistenceMapper {

    public CartJpaEntity toEntity(Cart domain) {
        CartJpaEntity entity = new CartJpaEntity(domain.getId(), domain.getCustomerId());
        for (CartItem item : domain.getItems()) {
            entity.addItem(new CartItemJpaEntity(entity, item.getProductId(), item.getQuantity(), item.getUnitPrice()));
        }
        return entity;
    }

    public void updateEntity(CartJpaEntity entity, Cart domain) {
        Map<Long, CartItemJpaEntity> existingByProduct = entity.getItems().stream()
                .collect(Collectors.toMap(CartItemJpaEntity::getProductId, Function.identity()));

        Set<Long> domainProductIds = domain.getItems().stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());

        List<CartItemJpaEntity> toRemove = entity.getItems().stream()
                .filter(item -> !domainProductIds.contains(item.getProductId()))
                .toList();
        toRemove.forEach(entity::removeItem);

        for (CartItem domainItem : domain.getItems()) {
            CartItemJpaEntity existing = existingByProduct.get(domainItem.getProductId());
            if (existing != null) {
                existing.setQuantity(domainItem.getQuantity());
                existing.setUnitPrice(domainItem.getUnitPrice());
            } else {
                entity.addItem(new CartItemJpaEntity(entity, domainItem.getProductId(), domainItem.getQuantity(), domainItem.getUnitPrice()));
            }
        }
    }

    public Cart toDomain(CartJpaEntity entity) {
        List<CartItem> items = entity.getItems().stream()
                .map(item -> CartItem.restore(item.getProductId(), item.getQuantity(), item.getUnitPrice()))
                .toList();
        return Cart.restore(entity.getId(), entity.getCustomerId(), items);
    }
}
