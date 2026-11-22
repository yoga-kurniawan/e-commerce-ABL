package id.ac.ub.abl.cart.adapter.inbound.rest;

import java.math.BigDecimal;
import java.util.Optional;
import id.ac.ub.abl.cart.adapter.outbound.database.CartsRepository;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.usecase.port.out.ProductLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CartsApiV2ControllerTest {
    private static final String BASE = "/api/v2/carts";
    @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DATABASE::getJdbcUrl);
        r.add("spring.datasource.username", DATABASE::getUsername);
        r.add("spring.datasource.password", DATABASE::getPassword);
    }
    @Autowired MockMvc http;
    @Autowired CartsRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @MockitoBean ProductLookup products;

    @BeforeEach void reset() {
        repository.deleteAll();
        when(products.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10)));
    }

    private long cartWithItem() throws Exception {
        var response = http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":101}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(response.getContentAsString()).get("id").longValue();
        http.perform(post(BASE + "/" + id + "/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].unitPrice").value(350000));
        return id;
    }

    @Test void putAndPatchReturnV2SnapshotWithoutChangingStoredUnitPrice() throws Exception {
        long id = cartWithItem();
        when(products.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("400000"), 10)));
        http.perform(put(BASE + "/" + id + "/items/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cartId").value(id))
                .andExpect(jsonPath("$.unitPrice").value(350000))
                .andExpect(jsonPath("$.subtotal").value(1050000));
        http.perform(patch(BASE + "/" + id + "/items/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unitPrice").value(350000))
                .andExpect(jsonPath("$.quantity").value(1));
        http.perform(get(BASE + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(350000));
        http.perform(get("/api/v1/carts/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").doesNotExist());
    }

    @Test void deleteOneAndClearAllKeepCartAndRemovePersistedItems() throws Exception {
        long id = cartWithItem();
        http.perform(delete(BASE + "/" + id + "/items/1")).andExpect(status().isNoContent());
        http.perform(delete(BASE + "/" + id + "/items/1")).andExpect(status().isNotFound());
        http.perform(post(BASE + "/" + id + "/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated());
        http.perform(delete(BASE + "/" + id + "/items")).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        http.perform(delete(BASE + "/" + id + "/items")).andExpect(status().isNoContent());
        http.perform(get(BASE + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(101))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
        assertThat(repository.existsById(id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id=?", Long.class, id)).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"quantity\":null}", "{\"quantity\":0}", "{\"quantity\":-1}", "{\"quantity\":1.5}"})
    void invalidUpdatesReturn400AndKeepQuantity(String body) throws Exception {
        long id = cartWithItem();
        http.perform(put(BASE + "/" + id + "/items/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        http.perform(patch(BASE + "/" + id + "/items/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        http.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test void insufficientStockAndMissingResourcesUseExpectedErrors() throws Exception {
        long id = cartWithItem();
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                put(BASE + "/" + id + "/items/1"), patch(BASE + "/" + id + "/items/1")}) {
            http.perform(request.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":11}"))
                    .andExpect(status().isConflict());
        }
        http.perform(put(BASE + "/" + id + "/items/999").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
        http.perform(delete(BASE + "/999999/items")).andExpect(status().isNotFound());
    }
}
