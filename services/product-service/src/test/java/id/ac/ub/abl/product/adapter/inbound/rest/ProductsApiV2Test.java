package id.ac.ub.abl.product.adapter.inbound.rest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import id.ac.ub.abl.product.adapter.outbound.database.ProductsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ProductsApiV2Test {
    private static final String BASE = "/api/v2/products";
    @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DATABASE::getJdbcUrl);
        r.add("spring.datasource.username", DATABASE::getUsername);
        r.add("spring.datasource.password", DATABASE::getPassword);
    }
    @Autowired MockMvc http;
    @Autowired ProductsRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Value("${local.server.port}") int port;

    @BeforeEach void reset() { repository.deleteAllInBatch(); }

    private long create(String name, String description) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("name", name);
        body.put("price", 350000.125);
        body.put("stock", 20);
        body.put("description", description);
        var response = http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.description").value(description))
                .andReturn().getResponse();
        long id = json.readTree(response.getContentAsString()).get("id").longValue();
        assertThat(response.getHeader("Location")).isEqualTo(BASE + "/" + id);
        return id;
    }

    @Test void versionsShareDataWhileV1WritesPreserveDescription() throws Exception {
        long id = create("Keyboard", "Mechanical 87 tombol");
        http.perform(get("/api/v1/products/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());
        http.perform(get("/api/v1/products")).andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Total-Count"))
                .andExpect(jsonPath("$[0].description").doesNotExist());
        http.perform(put("/api/v1/products/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard RGB\",\"price\":375000,\"stock\":15}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").doesNotExist());
        http.perform(patch("/api/v1/products/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stock\":10}"))
                .andExpect(status().isOk());
        http.perform(get(BASE + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Mechanical 87 tombol"))
                .andExpect(jsonPath("$.name").value("Keyboard RGB"))
                .andExpect(jsonPath("$.stock").value(10));
        assertThat(repository.findById(id).orElseThrow().getDescription()).isEqualTo("Mechanical 87 tombol");
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"name\":\"Mouse\",\"price\":0,\"stock\":0}",
            "{\"name\":\"Mouse\",\"price\":0,\"stock\":0,\"description\":null}"
    })
    void postAcceptsAbsentOrNullDescriptionAndReturnsTheField(String body) throws Exception {
        var response = http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var node = json.readTree(response.getContentAsString());
        assertThat(node.has("description")).isTrue();
        assertThat(node.get("description").isNull()).isTrue();
    }

    @Test void patchDistinguishesOmittedNullAndEmptyDescription() throws Exception {
        long id = create("Keyboard", "Keep");
        http.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":375000.125}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Keep"));
        http.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(""));
        http.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":null}"))
                .andExpect(status().isOk());
        assertThat(repository.findById(id).orElseThrow().getDescription()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"name\":\"New\",\"price\":1,\"stock\":2}",
            "{\"name\":\"New\",\"price\":1,\"stock\":2,\"description\":null}"
    })
    void putV2ClearsDescriptionWhenAbsentOrNull(String body) throws Exception {
        long id = create("Keyboard", "Remove");
        var result = http.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(result.getContentAsString()).get("description").isNull()).isTrue();
        assertThat(repository.findById(id).orElseThrow().getDescription()).isNull();
    }

    @Test void putAndPatchPersistNewDescriptionAndSoftDeleteKeepsHistory() throws Exception {
        long id = create("Keyboard", null);
        http.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RGB\",\"price\":0,\"stock\":0,\"description\":\"Switch merah\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Switch merah"));
        http.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Updated\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Updated"));
        http.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(repository.findById(id).orElseThrow().getDescription()).isEqualTo("Updated");
        assertThat(repository.findById(id).orElseThrow().isActive()).isFalse();
        http.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());
        http.perform(get("/api/v1/products/" + id)).andExpect(status().isNotFound());
        http.perform(get(BASE)).andExpect(header().string("X-Total-Count", "0"))
                .andExpect(content().json("[]"));
        http.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No revive\",\"price\":1,\"stock\":1}"))
                .andExpect(status().isNotFound());
        http.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"No revive\"}"))
                .andExpect(status().isNotFound());
        http.perform(delete(BASE + "/" + id)).andExpect(status().isNotFound());
    }

    @Test void filterCountsBeforePaginationAndMatchesLiteralText() throws Exception {
        long first = create("Keyboard B", null);
        long second = create("KEYBOARD A", "RGB");
        long hidden = create("Keyboard hidden", null);
        create("Mouse 100%_\\", null);
        http.perform(delete(BASE + "/" + hidden)).andExpect(status().isNoContent());
        http.perform(get(BASE).param("name", "keyBOARD").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(second));
        http.perform(get(BASE).param("name", "keyboard"))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[1].id").value(second));
        for (String filter : new String[]{"%", "_", "\\"}) {
            http.perform(get(BASE).param("name", filter)).andExpect(status().isOk())
                    .andExpect(header().string("X-Total-Count", "1"))
                    .andExpect(jsonPath("$[0].name").value("Mouse 100%_\\"));
        }
        http.perform(get(BASE).param("name", "unmatched"))
                .andExpect(header().string("X-Total-Count", "0")).andExpect(content().json("[]"));
        http.perform(get(BASE).param("name", "keyboard").param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"))
                .andExpect(content().json("[]"));
    }

    @Test void paginationIsOptInAndDefaultsApplyOnlyWhenRequested() throws Exception {
        for (int i = 0; i < 22; i++) { create("Item " + i, null); }
        http.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(22))
                .andExpect(header().string("X-Total-Count", "22"));
        http.perform(get(BASE).param("name", "")).andExpect(jsonPath("$.length()").value(22));
        http.perform(get(BASE).param("page", "0")).andExpect(jsonPath("$.length()").value(20));
        http.perform(get(BASE).param("page", "1")).andExpect(jsonPath("$.length()").value(2))
                .andExpect(header().string("X-Total-Count", "22"));
        http.perform(get(BASE).param("size", "3")).andExpect(jsonPath("$.length()").value(3));
    }

    @ParameterizedTest @ValueSource(strings = {
            "page=-1", "size=0", "size=101", "page=abc", "page=1.5", "size=1.5", "page=2147483648"
    })
    void invalidQueryReturns400(String query) throws Exception {
        http.perform(get(BASE + "?" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @ParameterizedTest @ValueSource(strings = {
            "{}", "{\"name\":null}", "{\"price\":null}", "{\"stock\":null}",
            "{\"price\":-1}", "{\"stock\":-1}", "{\"stock\":1.5}"
    })
    void invalidPatchDoesNotMutateProduct(String body) throws Exception {
        long id = create("Keyboard", "Keep");
        http.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertThat(repository.findById(id).orElseThrow().getDescription()).isEqualTo("Keep");
        assertThat(repository.findById(id).orElseThrow().getStock()).isEqualTo(20);
    }

    @Test void lengthLimitsAndRequiredFieldsAreValidatedBeforePersistence() throws Exception {
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"K\",\"price\":1,\"stock\":1,\"description\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
        long id = create("Boundary", "x".repeat(1000));
        http.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
        http.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"K\",\"price\":1,\"stock\":1,\"description\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
        http.perform(get(BASE).param("name", "x".repeat(256))).andExpect(status().isBadRequest());
        assertThat(repository.findById(id).orElseThrow().getDescription()).hasSize(1000);
        http.perform(get(BASE).param("name", "x".repeat(255))).andExpect(status().isOk());
        http.perform(get(BASE).param("size", "100")).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {
            "{}", "{\"price\":1,\"stock\":1}", "{\"name\":\"K\",\"stock\":1}",
            "{\"name\":\"K\",\"price\":1}", "{\"name\":null,\"price\":1,\"stock\":1}",
            "{\"name\":\"K\",\"price\":-1,\"stock\":1}",
            "{\"name\":\"K\",\"price\":1,\"stock\":-1}",
            "{\"name\":\"K\",\"price\":1,\"stock\":1.5}"
    })
    void invalidCreateAndReplaceDoNotPersistChanges(String body) throws Exception {
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertThat(repository.count()).isZero();
        long id = create("Original", "Keep");
        http.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        var stored = repository.findById(id).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Original");
        assertThat(stored.getDescription()).isEqualTo("Keep");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test void oldRowsHaveNullDescriptionAndRealHttpLocationCanBeFollowed() throws Exception {
        jdbc.update("INSERT INTO products(name,price,stock) VALUES ('Legacy', 1, 1)");
        var client = HttpClient.newHttpClient();
        URI base = URI.create("http://localhost:" + port + BASE);
        var created = client.send(HttpRequest.newBuilder(base).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Real HTTP\",\"price\":1,\"stock\":2,\"description\":\"Persisted\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);
        var detail = client.send(HttpRequest.newBuilder(base.resolve(created.headers().firstValue("Location").orElseThrow()))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(json.readTree(detail.body()).get("description").stringValue()).isEqualTo("Persisted");
        var response = http.perform(get(BASE)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString()).get(0).get("description").isNull()).isTrue();
    }
}
