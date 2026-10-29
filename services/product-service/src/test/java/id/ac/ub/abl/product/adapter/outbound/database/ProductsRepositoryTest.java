package id.ac.ub.abl.product.adapter.outbound.database;

import java.math.BigDecimal;

import id.ac.ub.abl.product.adapter.outbound.database.entity.ProductJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class ProductsRepositoryTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired ProductsRepository repository;

    @BeforeEach
    void clearProducts() {
        repository.deleteAllInBatch();
    }

    @Test
    void savesReadsUpdatesAndDeletesTheSameProduct() {
        var created = repository.saveAndFlush(
                new ProductJpaEntity(null, "Keyboard", new BigDecimal("350000.125"), 20));
        Long id = created.getId();
        assertNotNull(id);

        var stored = repository.findById(id).orElseThrow();
        assertEquals("Keyboard", stored.getName());
        assertEquals(0, new BigDecimal("350000.125").compareTo(stored.getPrice()));
        assertEquals(20, stored.getStock());

        repository.saveAndFlush(new ProductJpaEntity(id, "Mouse", new BigDecimal("150000"), 15));
        var updated = repository.findById(id).orElseThrow();
        assertEquals("Mouse", updated.getName());
        assertEquals(0, new BigDecimal("150000").compareTo(updated.getPrice()));
        assertEquals(15, updated.getStock());
        assertEquals(1, repository.count());

        repository.delete(updated);
        repository.flush();
        assertTrue(repository.findById(id).isEmpty());
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void listsAllProductsAndReturnsEmptyForMissingId() {
        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findById(Long.MAX_VALUE).isEmpty());

        var first = repository.saveAndFlush(new ProductJpaEntity(null, "Keyboard", BigDecimal.ONE, 1));
        var second = repository.saveAndFlush(new ProductJpaEntity(null, "Mouse", BigDecimal.ZERO, 0));

        var ids = repository.findAll().stream().map(ProductJpaEntity::getId).toList();
        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(java.util.List.of(first.getId(), second.getId())));
    }

    @Test
    void rejectsInvalidUpdateAndPreservesStoredProduct() {
        var created = repository.saveAndFlush(new ProductJpaEntity(null, "Keyboard", BigDecimal.ONE, 10));

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
                new ProductJpaEntity(created.getId(), "Keyboard", BigDecimal.ONE, -1)));

        assertEquals(10, repository.findById(created.getId()).orElseThrow().getStock());
    }
}
