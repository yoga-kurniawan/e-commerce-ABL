package id.ac.ub.abl.cart.adapter.outbound.database;

import java.math.BigDecimal;

import id.ac.ub.abl.cart.adapter.outbound.database.entity.CartItemJpaEntity;
import id.ac.ub.abl.cart.adapter.outbound.database.entity.CartJpaEntity;
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
class CartsRepositoryTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired CartsRepository repository;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void savesAndRetrievesCartWithItems() {
        CartJpaEntity cart = new CartJpaEntity(null, 101L);
        cart.addItem(new CartItemJpaEntity(cart, 1L, 2, new BigDecimal("350000")));
        CartJpaEntity saved = repository.save(cart);

        assertNotNull(saved.getId());
        assertEquals(1, repository.count());

        CartJpaEntity loaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals(101L, loaded.getCustomerId());
        assertEquals(1, loaded.getItems().size());
        assertEquals(1L, loaded.getItems().get(0).getProductId());
    }
}
