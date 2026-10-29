package id.ac.ub.abl.product.adapter.outbound.database;

import java.math.BigDecimal;

import id.ac.ub.abl.product.adapter.outbound.database.entity.ProductJpaEntity;
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
class ProductSchemaTest {
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
    void persistsAndReadsProductWithoutRoundingItsPrice() {
        Long id = transactions.execute(status -> {
            var entity = new ProductJpaEntity(null, "Keyboard", new BigDecimal("375000.125"), 20);
            entityManager.persist(entity);
            entityManager.flush();
            return entity.getId();
        });

        assertNotNull(id);
        transactions.executeWithoutResult(status -> {
            var stored = entityManager.find(ProductJpaEntity.class, id);
            assertEquals("Keyboard", stored.getName());
            assertEquals(0, new BigDecimal("375000.125").compareTo(stored.getPrice()));
            assertEquals(20, stored.getStock());
        });
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class));
    }

    @Test
    void databaseAcceptsZeroPriceAndStock() {
        assertEquals(1, jdbc.update("INSERT INTO products (name, price, stock) VALUES (?, ?, ?)",
                "Zero", BigDecimal.ZERO, 0));
    }

    @Test
    void databaseRejectsNegativeAndMissingRequiredValues() {
        assertInvalid("Keyboard", new BigDecimal("-0.01"), 1);
        assertInvalid("Keyboard", BigDecimal.ONE, -1);
        assertInvalid(null, BigDecimal.ONE, 1);
        assertInvalid("Keyboard", null, 1);
        assertInvalid("Keyboard", BigDecimal.ONE, null);
    }

    private void assertInvalid(String name, BigDecimal price, Integer stock) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO products (name, price, stock) VALUES (?, ?, ?)", name, price, stock));
    }
}
