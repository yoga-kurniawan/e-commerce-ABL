package id.ac.ub.abl.product.adapter.outbound.database.mapper;

import java.math.BigDecimal;

import id.ac.ub.abl.product.domain.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductPersistenceMapperTest {
    @Test
    void roundTripPreservesInactiveStatus() {
        var product = Product.restore(7L, "Keyboard", BigDecimal.ONE, 10, false);
        var entity = mapper.toEntity(product);
        assertFalse(entity.isActive());
        assertFalse(mapper.toDomain(entity).isActive());
    }

    private final ProductPersistenceMapper mapper = new ProductPersistenceMapper();

    @Test
    void mapsNewProductWithoutInventingAnId() {
        var entity = mapper.toEntity(Product.create("Keyboard", new BigDecimal("350000.125"), 20));

        assertNull(entity.getId());
        assertEquals("Keyboard", entity.getName());
        assertEquals(new BigDecimal("350000.125"), entity.getPrice());
        assertEquals(20, entity.getStock());
    }

    @Test
    void roundTripPreservesAllStoredProductAttributes() {
        var original = Product.restore(7L, "Keyboard", new BigDecimal("350000.125"), 0);
        var restored = mapper.toDomain(mapper.toEntity(original));

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getPrice(), restored.getPrice());
        assertEquals(original.getStock(), restored.getStock());
    }
}
