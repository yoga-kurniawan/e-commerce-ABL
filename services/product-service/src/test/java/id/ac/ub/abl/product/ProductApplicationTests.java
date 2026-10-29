package id.ac.ub.abl.product;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import id.ac.ub.abl.product.adapter.outbound.database.ProductsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductApplicationTests {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Value("${local.server.port}") int port;
    @Autowired ProductsRepository repository;
    @Autowired JsonMapper json;

	@Test
	void contextLoads() {
	    assertTrue(port > 0);
	}

    @Test
    void productLifecycleWorksOverRealHttpAndPreservesDeletedData() throws Exception {
        repository.deleteAllInBatch();
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var empty = send(client, "GET", "", null, 200);
        assertEquals(0, json.readTree(empty.body()).size());

        var created = send(client, "POST", "",
                "{\"name\":\"Keyboard\",\"price\":350000.125,\"stock\":20}", 201);
        var product = json.readTree(created.body());
        long id = product.get("id").longValue();
        String path = "/" + id;
        assertFalse(product.has("active"));
        assertEquals("http://localhost:" + port + "/api/v1/products" + path,
                created.headers().firstValue("Location").orElseThrow());

        var detail = json.readTree(send(client, "GET", path, null, 200).body());
        assertEquals("Keyboard", detail.get("name").stringValue());
        assertEquals(0, new BigDecimal("350000.125").compareTo(detail.get("price").decimalValue()));
        assertEquals(1, json.readTree(send(client, "GET", "", null, 200).body()).size());

        send(client, "PUT", path, "{\"name\":\"Mouse\",\"price\":150000,\"stock\":15}", 200);
        var patched = json.readTree(send(client, "PATCH", path, "{\"stock\":0}", 200).body());
        assertEquals("Mouse", patched.get("name").stringValue());
        assertEquals(150000, patched.get("price").intValue());
        assertEquals(0, patched.get("stock").intValue());

        for (String invalid : new String[]{"{}", "{\"stock\":null}", "{\"price\":-1}"}) {
            assertError(send(client, "PATCH", path, invalid, 400), "BAD_REQUEST");
        }
        assertError(send(client, "POST", "", "{", 400), "BAD_REQUEST");
        assertError(send(client, "POST", "", "{\"name\":\"Invalid\",\"price\":1,\"stock\":1.5}", 400), "BAD_REQUEST");
        assertError(send(client, "PATCH", path, "{\"stock\":1.5}", 400), "BAD_REQUEST");
        assertError(send(client, "GET", "/not-an-id", null, 400), "BAD_REQUEST");
        assertError(send(client, "GET", "/" + Long.MAX_VALUE, null, 404), "NOT_FOUND");

        var deleted = send(client, "DELETE", path, null, 204);
        assertEquals("", deleted.body());
        assertEquals(0, json.readTree(send(client, "GET", "", null, 200).body()).size());
        assertError(send(client, "GET", path, null, 404), "NOT_FOUND");
        assertError(send(client, "PATCH", path, "{\"stock\":1}", 404), "NOT_FOUND");
        assertError(send(client, "PUT", path, "{\"name\":\"New\",\"price\":1,\"stock\":1}", 404), "NOT_FOUND");
        assertError(send(client, "DELETE", path, null, 404), "NOT_FOUND");
        var historical = repository.findById(id).orElseThrow();
        assertFalse(historical.isActive());
        assertEquals("Mouse", historical.getName());
        assertEquals(0, historical.getStock());
        assertEquals(1, repository.count());
    }

    private HttpResponse<String> send(HttpClient client, String method, String path,
                                      String body, int expectedStatus) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/products" + path))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), () -> method + " " + path + ": " + response.body());
        return response;
    }

    private void assertError(HttpResponse<String> response, String code) {
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("application/json"));
        var error = json.readTree(response.body());
        assertEquals(2, error.size());
        assertEquals(code, error.get("code").stringValue());
        assertTrue(error.get("message").isString());
    }
}
