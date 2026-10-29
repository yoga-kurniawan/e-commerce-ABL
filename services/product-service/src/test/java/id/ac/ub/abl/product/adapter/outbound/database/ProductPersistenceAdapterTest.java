package id.ac.ub.abl.product.adapter.outbound.database;

import java.math.BigDecimal;

import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.usecase.port.out.ProductStore;
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
class ProductPersistenceAdapterTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired ProductStore store;
    @Autowired ProductsRepository repository;

    @BeforeEach
    void clearProducts() {
        repository.deleteAllInBatch();
    }

    @Test
    void createsAndReadsDomainProductWithGeneratedId() {
        var input = Product.create("Keyboard", new BigDecimal("350000.125"), 20);
        var saved = store.save(input);

        assertNull(input.getId());
        assertNotNull(saved.getId());
        var loaded = store.findById(saved.getId()).orElseThrow();
        assertEquals("Keyboard", loaded.getName());
        assertEquals(0, new BigDecimal("350000.125").compareTo(loaded.getPrice()));
        assertEquals(20, loaded.getStock());
    }

    @Test
    void updatesExistingProductWithoutCreatingADuplicate() {
        var saved = store.save(Product.create("Keyboard", BigDecimal.ONE, 20));
        var updated = store.save(saved.changePrice(new BigDecimal("375000.125")));

        assertEquals(saved.getId(), updated.getId());
        var loaded = store.findById(saved.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("375000.125").compareTo(loaded.getPrice()));
        assertEquals("Keyboard", loaded.getName());
        assertEquals(20, loaded.getStock());
        assertEquals(1, repository.count());
    }

    @Test
    void listsDomainProductsAndDeletesOnlyTheSelectedProduct() {
        assertTrue(store.findAll().isEmpty());
        assertTrue(store.findById(Long.MAX_VALUE).isEmpty());
        var first = store.save(Product.create("Keyboard", BigDecimal.ONE, 1));
        var second = store.save(Product.create("Mouse", BigDecimal.ZERO, 0));

        var ids = store.findAll().stream().map(Product::getId).toList();
        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(java.util.List.of(first.getId(), second.getId())));

        store.delete(first);

        assertTrue(store.findById(first.getId()).isEmpty());
        assertTrue(store.findById(second.getId()).isPresent());
        assertEquals(1, store.findAll().size());
        var historical = repository.findById(first.getId()).orElseThrow();
        assertFalse(historical.isActive());
        assertEquals("Keyboard", historical.getName());
        assertEquals(0, BigDecimal.ONE.compareTo(historical.getPrice()));
        assertEquals(1, historical.getStock());
        assertEquals(2, repository.count());
    }
}
