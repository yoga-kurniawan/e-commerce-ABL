package id.ac.ub.abl.product.adapter.inbound.rest;

import id.ac.ub.abl.product.adapter.outbound.database.ProductsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP dispatcher, use case, Flyway, dan PostgreSQL nyata dalam satu test. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductsApiControllerTest {
    private static final String BASE = "/api/v1/products";
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired MockMvc http;
    @Autowired ProductsRepository repository;
    @Autowired JsonMapper json;

    @BeforeEach
    void clearProducts() {
        repository.deleteAllInBatch();
    }

    @Test
    void createsProductAndServesItThroughDetailAndList() throws Exception {
        http.perform(get(BASE)).andExpect(status().isOk()).andExpect(content().json("[]"));
        long id = createProduct();

        http.perform(get(BASE + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.price").value(350000.125))
                .andExpect(jsonPath("$.stock").value(20))
                .andExpect(jsonPath("$.active").doesNotExist());
        http.perform(get(BASE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void putReplacesAllFieldsAndPatchPreservesOmittedFields() throws Exception {
        long id = createProduct();
        http.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mouse\",\"price\":150000,\"stock\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Mouse"))
                .andExpect(jsonPath("$.price").value(150000))
                .andExpect(jsonPath("$.stock").value(15));
        http.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":375000.125}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mouse"))
                .andExpect(jsonPath("$.price").value(375000.125))
                .andExpect(jsonPath("$.stock").value(15));
        http.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stock\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(0));
        var stored = repository.findById(id).orElseThrow();
        assertEquals(0, stored.getStock());
        assertEquals(1, repository.count());
    }

    @Test
    void deleteReturnsNoBodyAndRetainsInactiveDatabaseRow() throws Exception {
        long id = createProduct();
        http.perform(delete(BASE + "/{id}", id))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertFalse(repository.findById(id).orElseThrow().isActive());
        assertEquals(1, repository.count());
        http.perform(get(BASE)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"Keyboard\",\"price\":-1,\"stock\":20}",
            "{\"name\":\"Keyboard\",\"price\":1,\"stock\":-1}"})
    void invalidCreateAndPutReturn400WithoutChangingData(String body) throws Exception {
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, repository.count());
        long id = createProduct();
        http.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals("Keyboard", repository.findById(id).orElseThrow().getName());
        assertEquals(20, repository.findById(id).orElseThrow().getStock());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"price\":null}", "{\"stock\":null}",
            "{\"price\":-1}", "{\"stock\":-1}"})
    void invalidPatchReturns400WithoutChangingData(String body) throws Exception {
        long id = createProduct();
        http.perform(patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(20, repository.findById(id).orElseThrow().getStock());
        assertTrue(repository.findById(id).orElseThrow().isActive());
    }

    @Test
    void malformedJsonAndNonIntegerPathReturn400() throws Exception {
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        http.perform(get(BASE + "/not-an-id")).andExpect(status().isBadRequest());
        assertEquals(0, repository.count());
    }

    private long createProduct() throws Exception {
        var result = http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":350000.125,\"stock\":20}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.active").doesNotExist())
                .andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").longValue();
        assertEquals("http://localhost" + BASE + "/" + id, result.getResponse().getHeader("Location"));
        return id;
    }
}
