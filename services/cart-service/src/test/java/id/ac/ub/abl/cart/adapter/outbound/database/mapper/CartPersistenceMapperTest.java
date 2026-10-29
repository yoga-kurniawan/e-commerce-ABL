package id.ac.ub.abl.cart.adapter.outbound.database.mapper;

import java.math.BigDecimal;
import java.util.List;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.CartItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CartPersistenceMapperTest {
    private final CartPersistenceMapper mapper = new CartPersistenceMapper();

    @Test
    void mapsNewCartWithoutInventingAnId() {
        Cart cart = Cart.create(101L);
        var entity = mapper.toEntity(cart);

        assertNull(entity.getId());
        assertEquals(101L, entity.getCustomerId());
        assertTrue(entity.getItems().isEmpty());
    }

    @Test
    void roundTripPreservesAllStoredCartAndItemAttributes() {
        CartItem item1 = CartItem.restore(1L, 2, new BigDecimal("350000.125"));
        CartItem item2 = CartItem.restore(2L, 1, new BigDecimal("150000.00"));
        Cart original = Cart.restore(7L, 101L, List.of(item1, item2));

        var entity = mapper.toEntity(original);
        Cart restored = mapper.toDomain(entity);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getCustomerId(), restored.getCustomerId());
        assertEquals(2, restored.getItems().size());

        CartItem rItem1 = restored.findItem(1L).orElseThrow();
        assertEquals(2, rItem1.getQuantity());
        assertEquals(new BigDecimal("350000.125"), rItem1.getUnitPrice());
        assertEquals(new BigDecimal("700000.250"), rItem1.getSubtotal());

        assertEquals(original.getTotal(), restored.getTotal());
    }
}
