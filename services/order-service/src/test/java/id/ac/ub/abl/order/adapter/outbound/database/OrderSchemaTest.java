package id.ac.ub.abl.order.adapter.outbound.database;

import java.math.BigDecimal;

import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderItemJpaEntity;
import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderJpaEntity;
import id.ac.ub.abl.order.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

/** Flyway dan Hibernate validate dijalankan pada PostgreSQL test yang terisolasi. */
@SpringBootTest
@Testcontainers
class OrderSchemaTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsAndReadsOrderWithItemSnapshot() {
        Long orderId = transactions.execute(status -> {
            OrderJpaEntity order = new OrderJpaEntity(null, 101L, 10L, OrderStatus.PENDING, null);
            order.addItem(new OrderItemJpaEntity(order, 1L, "Keyboard", 2, new BigDecimal("350000.125")));
            entityManager.persist(order);
            entityManager.flush();
            return order.getId();
        });

        assertNotNull(orderId);
        transactions.executeWithoutResult(status -> {
            OrderJpaEntity stored = entityManager.find(OrderJpaEntity.class, orderId);
            assertEquals(101L, stored.getCustomerId());
            assertEquals(10L, stored.getCartId());
            assertEquals(OrderStatus.PENDING, stored.getStatus());
            assertNull(stored.getShippingAddress());
            assertEquals(1, stored.getItems().size());
            OrderItemJpaEntity item = stored.getItems().get(0);
            assertEquals("Keyboard", item.getProductName());
            assertEquals(0, new BigDecimal("350000.125").compareTo(item.getUnitPrice()));
        });
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class));
    }

    @Test
    void databaseRejectsUnknownStatusAndBlankAddress() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO orders (customer_id, cart_id, status) VALUES (101, 10, 'PAID')"));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO orders (customer_id, cart_id, status, shipping_address) VALUES (101, 10, 'PENDING', '  ')"));
    }

    @Test
    void databaseRejectsInvalidItems() {
        Long orderId = insertOrder();

        assertThrows(DataIntegrityViolationException.class, () -> insertItem(orderId, 1L, 0, BigDecimal.TEN));
        assertThrows(DataIntegrityViolationException.class, () -> insertItem(orderId, 1L, 1, new BigDecimal("-1")));
        insertItem(orderId, 1L, 1, BigDecimal.TEN);
        assertThrows(DataIntegrityViolationException.class, () -> insertItem(orderId, 1L, 2, BigDecimal.TEN));
    }

    @Test
    void deletingOrderCascadesToItems() {
        Long orderId = insertOrder();
        insertItem(orderId, 1L, 1, BigDecimal.TEN);

        jdbc.update("DELETE FROM orders WHERE id = ?", orderId);

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id = ?", Integer.class, orderId));
    }

    private Long insertOrder() {
        return jdbc.queryForObject(
                "INSERT INTO orders (customer_id, cart_id, status) VALUES (101, 10, 'PENDING') RETURNING id", Long.class);
    }

    private void insertItem(Long orderId, Long productId, int quantity, BigDecimal unitPrice) {
        jdbc.update("INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price) VALUES (?, ?, 'Produk', ?, ?)",
                orderId, productId, quantity, unitPrice);
    }
}
