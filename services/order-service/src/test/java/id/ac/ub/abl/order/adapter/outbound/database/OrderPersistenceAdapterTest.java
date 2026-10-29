package id.ac.ub.abl.order.adapter.outbound.database;

import java.math.BigDecimal;
import java.util.List;

import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.domain.OrderItem;
import id.ac.ub.abl.order.domain.OrderStatus;
import id.ac.ub.abl.order.usecase.port.out.OrderStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class OrderPersistenceAdapterTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired OrderStore store;
    @Autowired OrdersRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    private Order newOrder() {
        return Order.place(101L, 10L, List.of(
                OrderItem.create(1L, "Keyboard", 2, new BigDecimal("350000.125")),
                OrderItem.create(2L, "Mouse", 1, new BigDecimal("150000"))));
    }

    @Test
    void savesNewOrderWithGeneratedIdAndItemSnapshot() {
        Order saved = store.save(newOrder());

        assertNotNull(saved.getId());
        assertEquals(OrderStatus.PENDING, saved.getStatus());
        assertEquals(0, new BigDecimal("850000.250").compareTo(saved.getTotal()));

        Order loaded = store.findById(saved.getId()).orElseThrow();
        assertEquals(2, loaded.getItems().size());
        assertEquals("Keyboard", loaded.getItems().get(0).getProductName());
        assertEquals(0, new BigDecimal("350000.125").compareTo(loaded.getItems().get(0).getUnitPrice()));
    }

    @Test
    void updatesShippingAddressWithoutDuplicatingItems() {
        Order saved = store.save(newOrder());

        Order updated = store.save(saved.changeShippingAddress("Jl. Veteran No. 10, Malang"));

        assertEquals(saved.getId(), updated.getId());
        assertEquals("Jl. Veteran No. 10, Malang", store.findById(saved.getId()).orElseThrow().getShippingAddress());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id = ?",
                Integer.class, saved.getId()));
    }

    @Test
    void failedItemInsertRollsBackWholeOrder() {
        jdbc.execute("ALTER TABLE order_items ADD CONSTRAINT test_reject_mouse CHECK (product_name <> 'Mouse')");
        try {
            assertThrows(RuntimeException.class, () -> store.save(newOrder()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM order_items", Integer.class));
        } finally {
            jdbc.execute("ALTER TABLE order_items DROP CONSTRAINT test_reject_mouse");
        }
    }

    @Test
    void findByIdReturnsEmptyForUnknownOrder() {
        assertTrue(store.findById(999_999L).isEmpty());
    }
}
