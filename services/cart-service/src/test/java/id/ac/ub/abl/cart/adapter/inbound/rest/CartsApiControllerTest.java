package id.ac.ub.abl.cart.adapter.inbound.rest;

import java.math.BigDecimal;
import java.util.Optional;

import id.ac.ub.abl.cart.adapter.outbound.database.CartsRepository;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.usecase.port.out.ProductLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
class CartsApiControllerTest {
    private static final String BASE = "/api/v1/carts";

    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired MockMvc http;
    @Autowired CartsRepository repository;
    @Autowired JsonMapper json;

    @MockitoBean
    ProductLookup productLookup;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void createCart_createsEmptyCartWithBackendIdAndZeroTotal() throws Exception {
        var result = http.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":101}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerId").value(101))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andReturn();

        long id = json.readTree(result.getResponse().getContentAsString()).get("id").longValue();
        assertEquals("http://localhost" + BASE + "/" + id, result.getResponse().getHeader("Location"));
        assertEquals(1, repository.count());
    }

    @Test
    void createCart_withoutCustomerId_returns400() throws Exception {
        http.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertEquals(0, repository.count());
    }

    @Test
    void getCart_whenExists_returnsCartDetails() throws Exception {
        long cartId = createCart(101L);

        http.perform(get(BASE + "/{cartId}", cartId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartId))
                .andExpect(jsonPath("$.customerId").value(101))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void getCart_whenNotFound_returns404() throws Exception {
        http.perform(get(BASE + "/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Keranjang tidak ditemukan"));
    }

    @Test
    void addCartItem_newProduct_calculatesSubtotalAndTotal() throws Exception {
        long cartId = createCart(101L);
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10)));

        http.perform(post(BASE + "/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(cartId))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(700000))
                .andExpect(jsonPath("$.total").value(700000));
    }

    @Test
    void addCartItem_existingProduct_increasesQuantityAndRetainsUnitPrice() throws Exception {
        long cartId = createCart(101L);
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10)));

        // Tambah pertama: 2 item @ 350.000
        http.perform(post(BASE + "/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated());

        // Simulasi perubahan harga di katalog produk
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("400000"), 10)));

        // Tambah kedua: 3 item lagi -> quantity akhir 5, harga tersimpan tetap 350.000
        http.perform(post(BASE + "/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.items[0].subtotal").value(1750000))
                .andExpect(jsonPath("$.total").value(1750000));
    }

    @Test
    void addCartItem_whenStockInsufficient_returns409() throws Exception {
        long cartId = createCart(101L);
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 3)));

        http.perform(post(BASE + "/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Jumlah melebihi stok yang tersedia"));
    }

    @Test
    void addCartItem_whenProductNotFound_returns404() throws Exception {
        long cartId = createCart(101L);
        when(productLookup.findById(999L)).thenReturn(Optional.empty());

        http.perform(post(BASE + "/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":999,\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Produk tidak ditemukan"));
    }

    @Test
    void replaceCartItem_success_updatesQuantityAndSubtotal() throws Exception {
        long cartId = createCart(101L);
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10)));

        http.perform(post(BASE + "/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated());

        http.perform(put(BASE + "/{cartId}/items/{productId}", cartId, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(cartId))
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.subtotal").value(1400000));
    }

    @Test
    void replaceCartItem_whenStockInsufficient_returns409() throws Exception {
        long cartId = createCart(101L);
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 5)));

        http.perform(post(BASE + "/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated());

        http.perform(put(BASE + "/{cartId}/items/{productId}", cartId, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":8}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void patchCartItem_success() throws Exception {
        long cartId = createCart(101L);
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10)));

        http.perform(post(BASE + "/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated());

        http.perform(patch(BASE + "/{cartId}/items/{productId}", cartId, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.subtotal").value(1050000));
    }

    @Test
    void patchCartItem_whenEmpty_returns400() throws Exception {
        long cartId = createCart(101L);
        http.perform(patch(BASE + "/{cartId}/items/1", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void deleteCartItem_success_removesItemAndUpdatesTotal() throws Exception {
        long cartId = createCart(101L);
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10)));
        when(productLookup.findById(2L)).thenReturn(Optional.of(
                new ProductSnapshot(2L, "Mouse", new BigDecimal("150000"), 10)));

        http.perform(post(BASE + "/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated());
        http.perform(post(BASE + "/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2,\"quantity\":1}"))
                .andExpect(status().isCreated());

        // Hapus produk 1
        http.perform(delete(BASE + "/{cartId}/items/{productId}", cartId, 1L))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // Periksa isi keranjang sekarang
        http.perform(get(BASE + "/{cartId}", cartId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(2))
                .andExpect(jsonPath("$.total").value(150000));
    }

    @Test
    void deleteCartItem_whenItemNotFound_returns404() throws Exception {
        long cartId = createCart(101L);
        http.perform(delete(BASE + "/{cartId}/items/999", cartId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private long createCart(Long customerId) throws Exception {
        var result = http.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + customerId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").longValue();
    }
}
