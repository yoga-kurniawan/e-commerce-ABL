package id.ac.ub.abl.order;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import id.ac.ub.abl.order.adapter.outbound.database.OrdersRepository;
import id.ac.ub.abl.order.domain.CartItemSnapshot;
import id.ac.ub.abl.order.domain.CartSnapshot;
import id.ac.ub.abl.order.domain.ProductSnapshot;
import id.ac.ub.abl.order.usecase.port.out.CartLookup;
import id.ac.ub.abl.order.usecase.port.out.ProductLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderApplicationTests {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Value("${local.server.port}") int port;
    @Autowired OrdersRepository repository;
    @Autowired JsonMapper json;

    @MockitoBean CartLookup cartLookup;
    @MockitoBean ProductLookup productLookup;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertTrue(port > 0);
    }

    @Test
    void orderLifecycleWorksOverRealHttp() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        when(cartLookup.findById(10L)).thenReturn(Optional.of(new CartSnapshot(10L, 101L, List.of(
                new CartItemSnapshot(1L, 2, new BigDecimal("700000")),
                new CartItemSnapshot(2L, 1, new BigDecimal("150000"))))));
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10)));
        when(productLookup.findById(2L)).thenReturn(Optional.of(
                new ProductSnapshot(2L, "Mouse", new BigDecimal("150000"), 10)));

        // 1. Create order
        var created = send(client, "POST", "", "{\"cartId\":10,\"customerId\":101}", 201);
        var order = json.readTree(created.body());
        long orderId = order.get("id").longValue();
        assertEquals("http://localhost:" + port + "/api/v1/orders/" + orderId,
                created.headers().firstValue("Location").orElseThrow());
        assertEquals(850000, order.get("total").intValue());
        assertEquals("PENDING", order.get("status").stringValue());

        // 2. Catalog price changes do not affect the stored order
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("999999"), 10)));
        var fetched = json.readTree(send(client, "GET", "/" + orderId, null, 200).body());
        assertEquals(850000, fetched.get("total").intValue());

        // 3. Update shipping address
        var patched = json.readTree(send(client, "PATCH", "/" + orderId,
                "{\"shippingAddress\":\"Jl. Veteran No. 10, Malang\"}", 200).body());
        assertEquals("Jl. Veteran No. 10, Malang", patched.get("shippingAddress").stringValue());

        // 4. Unknown order and unsupported method
        send(client, "GET", "/999999", null, 404);
        send(client, "DELETE", "/" + orderId, null, 405);
    }

    private HttpResponse<String> send(HttpClient client, String method, String path,
                                      String body, int expectedStatus) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/orders" + path))
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
}
