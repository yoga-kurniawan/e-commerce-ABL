package id.ac.ub.abl.order.adapter.outbound.database;

import java.math.BigDecimal;

import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderItemJpaEntity;
import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderJpaEntity;
import id.ac.ub.abl.order.domain.OrderStatus;
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
class OrdersRepositoryTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired OrdersRepository repository;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void savesOrderWithItemsThroughCascade() {
        OrderJpaEntity order = new OrderJpaEntity(null, 101L, 10L, OrderStatus.PENDING, null);
        order.addItem(new OrderItemJpaEntity(order, 1L, "Keyboard", 2, new BigDecimal("350000")));
        order.addItem(new OrderItemJpaEntity(order, 2L, "Mouse", 1, new BigDecimal("150000")));

        OrderJpaEntity saved = repository.save(order);

        OrderJpaEntity loaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals(2, loaded.getItems().size());
        assertEquals(1L, loaded.getItems().get(0).getProductId());
        assertEquals(2L, loaded.getItems().get(1).getProductId());
    }

    @Test
    void findByIdReturnsEmptyForUnknownOrder() {
        assertTrue(repository.findById(999_999L).isEmpty());
    }

    @Test
    void deleteAllRemovesOrdersAndItems() {
        OrderJpaEntity order = new OrderJpaEntity(null, 101L, 10L, OrderStatus.PENDING, null);
        order.addItem(new OrderItemJpaEntity(order, 1L, "Keyboard", 1, BigDecimal.TEN));
        repository.save(order);

        repository.deleteAll();

        assertEquals(0, repository.count());
    }
}
