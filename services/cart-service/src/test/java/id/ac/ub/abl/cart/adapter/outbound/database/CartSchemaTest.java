package id.ac.ub.abl.cart.adapter.outbound.database;

import java.math.BigDecimal;

import id.ac.ub.abl.cart.adapter.outbound.database.entity.CartItemJpaEntity;
import id.ac.ub.abl.cart.adapter.outbound.database.entity.CartJpaEntity;
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
class CartSchemaTest {
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
    void persistsAndReadsCartAndItems() {
        Long cartId = transactions.execute(status -> {
            CartJpaEntity cart = new CartJpaEntity(null, 101L);
            CartItemJpaEntity item = new CartItemJpaEntity(cart, 1L, 2, new BigDecimal("350000.125"));
            cart.addItem(item);
            entityManager.persist(cart);
            entityManager.flush();
            return cart.getId();
        });

        assertNotNull(cartId);
        transactions.executeWithoutResult(status -> {
            CartJpaEntity stored = entityManager.find(CartJpaEntity.class, cartId);
            assertEquals(101L, stored.getCustomerId());
            assertEquals(1, stored.getItems().size());
            CartItemJpaEntity item = stored.getItems().get(0);
            assertEquals(1L, item.getProductId());
            assertEquals(2, item.getQuantity());
            assertEquals(0, new BigDecimal("350000.125").compareTo(item.getUnitPrice()));
        });
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class));
    }

    @Test
    void databaseRejectsQuantityLessThanOne() {
        Long cartId = jdbc.queryForObject("INSERT INTO carts (customer_id) VALUES (101) RETURNING id", Long.class);

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO cart_items (cart_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                        cartId, 1L, 0, BigDecimal.TEN));

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO cart_items (cart_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                        cartId, 1L, -1, BigDecimal.TEN));
    }

    @Test
    void databaseRejectsNegativeUnitPrice() {
        Long cartId = jdbc.queryForObject("INSERT INTO carts (customer_id) VALUES (102) RETURNING id", Long.class);

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO cart_items (cart_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                        cartId, 1L, 2, new BigDecimal("-1.00")));
    }

    @Test
    void databaseRejectsDuplicateProductInSameCart() {
        Long cartId = jdbc.queryForObject("INSERT INTO carts (customer_id) VALUES (103) RETURNING id", Long.class);

        jdbc.update("INSERT INTO cart_items (cart_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                cartId, 1L, 2, BigDecimal.TEN);

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO cart_items (cart_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                        cartId, 1L, 3, BigDecimal.TEN));
    }

    @Test
    void cascadeDeleteRemovesItemsWhenCartIsDeleted() {
        Long cartId = jdbc.queryForObject("INSERT INTO carts (customer_id) VALUES (104) RETURNING id", Long.class);
        jdbc.update("INSERT INTO cart_items (cart_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                cartId, 1L, 2, BigDecimal.TEN);

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id = ?", Integer.class, cartId));

        jdbc.update("DELETE FROM carts WHERE id = ?", cartId);

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id = ?", Integer.class, cartId));
    }
}
