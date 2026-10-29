package id.ac.ub.abl.order.adapter.inbound.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import id.ac.ub.abl.order.adapter.outbound.database.OrdersRepository;
import id.ac.ub.abl.order.domain.CartItemSnapshot;
import id.ac.ub.abl.order.domain.CartSnapshot;
import id.ac.ub.abl.order.domain.ProductSnapshot;
import id.ac.ub.abl.order.usecase.exception.CartServiceUnavailableException;
import id.ac.ub.abl.order.usecase.port.out.CartLookup;
import id.ac.ub.abl.order.usecase.port.out.ProductLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrdersApiControllerTest {
    private static final String BASE = "/api/v1/orders";

    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired MockMvc http;
    @Autowired OrdersRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @MockitoBean CartLookup cartLookup;
    @MockitoBean ProductLookup productLookup;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard Mechanical", new BigDecimal("400000"), 20)));
        when(cartLookup.findById(10L)).thenReturn(Optional.of(new CartSnapshot(10L, 101L,
                List.of(new CartItemSnapshot(1L, 2, new BigDecimal("700000"))))));
    }

    private long createOrder() throws Exception {
        var result = http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":10,\"customerId\":101}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").longValue();
    }

    @Test
    void createOrder_persistsSnapshotWithCartPrice() throws Exception {
        var result = http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":10,\"customerId\":101}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(101))
                .andExpect(jsonPath("$.total").value(700000))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.shippingAddress").isEmpty())
                .andReturn();

        long id = json.readTree(result.getResponse().getContentAsString()).get("id").longValue();
        assertEquals("http://localhost" + BASE + "/" + id, result.getResponse().getHeader("Location"));
        var item = jdbc.queryForMap("SELECT product_name, quantity, unit_price FROM order_items WHERE order_id = ?", id);
        assertEquals("Keyboard Mechanical", item.get("product_name"));
        assertEquals(2, item.get("quantity"));
        assertEquals(0, new BigDecimal("350000").compareTo((BigDecimal) item.get("unit_price")));
    }

    @Test
    void createOrder_failuresDoNotPersistAnything() throws Exception {
        when(cartLookup.findById(99L)).thenReturn(Optional.empty());
        when(cartLookup.findById(11L)).thenReturn(Optional.of(new CartSnapshot(11L, 101L, List.of())));
        when(cartLookup.findById(12L)).thenThrow(new CartServiceUnavailableException("down", null));

        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":99,\"customerId\":101}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Keranjang tidak ditemukan"));
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":11,\"customerId\":101}"))
                .andExpect(status().isConflict());
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":10,\"customerId\":202}"))
                .andExpect(status().isConflict());
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":12,\"customerId\":101}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":\"abc\",\"customerId\":101}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, repository.count());
    }

    @Test
    void getOrder_returnsStoredOrderOr404() throws Exception {
        long id = createOrder();

        http.perform(get(BASE + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.total").value(700000));
        http.perform(get(BASE + "/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void patchOrder_changesAddressButIgnoresTotalAndStatus() throws Exception {
        long id = createOrder();

        http.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\":\"Jl. Veteran No. 10, Malang\",\"total\":1,\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddress").value("Jl. Veteran No. 10, Malang"))
                .andExpect(jsonPath("$.total").value(700000))
                .andExpect(jsonPath("$.status").value("PENDING"));

        http.perform(get(BASE + "/{id}", id))
                .andExpect(jsonPath("$.shippingAddress").value("Jl. Veteran No. 10, Malang"));
    }

    @Test
    void patchOrder_rejectsInvalidBodiesAndNonPendingOrders() throws Exception {
        long id = createOrder();

        http.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        http.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"shippingAddress\":\" \"}"))
                .andExpect(status().isBadRequest());
        http.perform(patch(BASE + "/{id}", 999_999L).contentType(MediaType.APPLICATION_JSON).content("{\"shippingAddress\":\"X\"}"))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE orders SET status = 'CONFIRMED' WHERE id = ?", id);
        http.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"shippingAddress\":\"Alamat baru\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertNull(jdbc.queryForObject("SELECT shipping_address FROM orders WHERE id = ?", String.class, id));
    }
}
