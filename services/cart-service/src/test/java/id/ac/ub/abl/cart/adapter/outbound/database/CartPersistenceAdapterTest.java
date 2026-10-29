package id.ac.ub.abl.cart.adapter.outbound.database;

import java.math.BigDecimal;
import java.util.List;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.CartItem;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class CartPersistenceAdapterTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired CartStore cartStore;
    @Autowired CartsRepository cartsRepository;

    @BeforeEach
    void clearDatabase() {
        cartsRepository.deleteAll();
    }

    @Test
    void createsAndReadsEmptyCartWithGeneratedId() {
        Cart cart = Cart.create(101L);
        Cart saved = cartStore.save(cart);

        assertNull(cart.getId());
        assertNotNull(saved.getId());
        assertEquals(101L, saved.getCustomerId());
        assertTrue(saved.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, saved.getTotal());

        Cart loaded = cartStore.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getId(), loaded.getId());
        assertEquals(101L, loaded.getCustomerId());
        assertTrue(loaded.getItems().isEmpty());
    }

    @Test
    void persistsCartWithItemsAndCalculatedSubtotals() {
        Cart cart = Cart.create(101L);
        Cart saved = cartStore.save(cart);

        ProductSnapshot p1 = new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000.125"), 10);
        ProductSnapshot p2 = new ProductSnapshot(2L, "Mouse", new BigDecimal("150000"), 10);
        Cart withItems = saved.addItem(p1, 2).addItem(p2, 1);

        Cart updated = cartStore.save(withItems);

        assertEquals(saved.getId(), updated.getId());
        assertEquals(2, updated.getItems().size());
        assertEquals(new BigDecimal("850000.250"), updated.getTotal());

        Cart reloaded = cartStore.findById(saved.getId()).orElseThrow();
        assertEquals(2, reloaded.getItems().size());
        CartItem item1 = reloaded.findItem(1L).orElseThrow();
        assertEquals(2, item1.getQuantity());
        assertEquals(0, new BigDecimal("350000.125").compareTo(item1.getUnitPrice()));
        assertEquals(0, new BigDecimal("700000.250").compareTo(item1.getSubtotal()));
    }

    @Test
    void updatesItemQuantityAndRemovesItemCleanly() {
        Cart cart = Cart.create(101L);
        ProductSnapshot p1 = new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10);
        ProductSnapshot p2 = new ProductSnapshot(2L, "Mouse", new BigDecimal("150000"), 10);
        Cart initial = cartStore.save(cart.addItem(p1, 2).addItem(p2, 1));

        // Update quantity item 1
        Cart updatedQty = cartStore.save(initial.replaceItemQuantity(1L, 4, 10));
        assertEquals(4, updatedQty.findItem(1L).orElseThrow().getQuantity());

        // Remove item 2
        Cart removedItem2 = cartStore.save(updatedQty.removeItem(2L));
        assertEquals(1, removedItem2.getItems().size());
        assertTrue(removedItem2.findItem(2L).isEmpty());

        Cart reloaded = cartStore.findById(initial.getId()).orElseThrow();
        assertEquals(1, reloaded.getItems().size());
        assertEquals(1L, reloaded.getItems().get(0).getProductId());
        assertEquals(4, reloaded.getItems().get(0).getQuantity());
    }
}
