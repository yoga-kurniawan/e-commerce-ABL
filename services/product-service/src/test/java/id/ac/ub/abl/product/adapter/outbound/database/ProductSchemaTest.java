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
import org.flywaydb.core.Flyway;
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
    void upgradingV2DataToDescriptionPreservesExistingRows() {
        var old = Flyway.configure()
                .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas("description_upgrade_test").target("2").load();
        old.migrate();
        jdbc.update("INSERT INTO description_upgrade_test.products(name,price,stock) VALUES ('Existing', 350000.125, 20)");
        jdbc.update("INSERT INTO description_upgrade_test.products(name,price,stock,active) VALUES ('History', 1, 0, false)");
        var latest = Flyway.configure()
                .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas("description_upgrade_test").target("3").load();
        latest.migrate();
        latest.validate();
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM description_upgrade_test.products WHERE description IS NULL", Integer.class));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT active FROM description_upgrade_test.products WHERE name='History'", Boolean.class));
        assertEquals(0, new BigDecimal("350000.125").compareTo(jdbc.queryForObject(
                "SELECT price FROM description_upgrade_test.products WHERE name='Existing'", BigDecimal.class)));
        latest.migrate();
        assertEquals("3", latest.info().current().getVersion().getVersion());
    }

    @Test
    void upgradingV3DataToVersionAndConstraintsPreservesExistingRows() {
        var v3 = Flyway.configure()
                .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas("version_upgrade_test").target("3").load();
        v3.migrate();
        jdbc.update("INSERT INTO version_upgrade_test.products(name,price,stock,description) VALUES ('Existing', 350000.125, 20, 'Desc')");

        var latest = Flyway.configure()
                .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas("version_upgrade_test").load();
        latest.migrate();
        latest.validate();

        assertEquals("4", latest.info().current().getVersion().getVersion());
        assertEquals(0L, jdbc.queryForObject(
                "SELECT version FROM version_upgrade_test.products WHERE name = 'Existing'", Long.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'version_upgrade_test' AND indexname = 'idx_products_active_id'",
                Integer.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO version_upgrade_test.products(name,price,stock) VALUES ('   ', 1, 1)"));
    }

    @Test
    void upgradingExistingProductsDefaultsThemToActive() throws Exception {
        var initial = Flyway.configure()
                .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas("upgrade_test").target("1").load();
        initial.migrate();
        jdbc.update("INSERT INTO upgrade_test.products (name, price, stock) VALUES ('Existing', 1, 10)");

        Flyway.configure()
                .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas("upgrade_test").load().migrate();

        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT active FROM upgrade_test.products WHERE name = 'Existing'", Boolean.class));
    }

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
        assertInvalid("", BigDecimal.ONE, 1);
        assertInvalid("   ", BigDecimal.ONE, 1);
        assertInvalid("Keyboard", null, 1);
        assertInvalid("Keyboard", BigDecimal.ONE, null);
    }

    private void assertInvalid(String name, BigDecimal price, Integer stock) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO products (name, price, stock) VALUES (?, ?, ?)", name, price, stock));
    }
}
