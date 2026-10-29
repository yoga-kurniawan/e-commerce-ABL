package id.ac.ub.abl.cart;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import id.ac.ub.abl.cart.adapter.outbound.database.CartsRepository;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.usecase.port.out.ProductLookup;
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
class CartApplicationTests {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Value("${local.server.port}") int port;
    @Autowired CartsRepository repository;
    @Autowired JsonMapper json;

    @MockitoBean
    ProductLookup productLookup;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertTrue(port > 0);
    }

    @Test
    void cartLifecycleWorksOverRealHttp() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        // 1. Create cart
        var created = send(client, "POST", "", "{\"customerId\":101}", 201);
        var cart = json.readTree(created.body());
        long cartId = cart.get("id").longValue();
        assertEquals(101, cart.get("customerId").intValue());
        assertEquals(0, cart.get("items").size());
        assertEquals(0, cart.get("total").intValue());

        // 2. Mock product lookup
        when(productLookup.findById(1L)).thenReturn(Optional.of(
                new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10)));
        when(productLookup.findById(2L)).thenReturn(Optional.of(
                new ProductSnapshot(2L, "Mouse", new BigDecimal("150000"), 10)));

        // 3. Add items
        var item1Response = send(client, "POST", "/" + cartId + "/items", "{\"productId\":1,\"quantity\":2}", 201);
        var cartWithItem1 = json.readTree(item1Response.body());
        assertEquals(1, cartWithItem1.get("items").size());
        assertEquals(700000, cartWithItem1.get("total").intValue());

        send(client, "POST", "/" + cartId + "/items", "{\"productId\":2,\"quantity\":1}", 201);

        // 4. Get cart
        var getResponse = send(client, "GET", "/" + cartId, null, 200);
        var cartDetails = json.readTree(getResponse.body());
        assertEquals(2, cartDetails.get("items").size());
        assertEquals(850000, cartDetails.get("total").intValue());

        // 5. Replace item quantity
        var putResponse = send(client, "PUT", "/" + cartId + "/items/1", "{\"quantity\":3}", 200);
        var putResult = json.readTree(putResponse.body());
        assertEquals(3, putResult.get("quantity").intValue());
        assertEquals(1050000, putResult.get("subtotal").intValue());

        // 6. Delete item
        send(client, "DELETE", "/" + cartId + "/items/1", null, 204);

        // 7. Verify remaining item
        var finalCart = json.readTree(send(client, "GET", "/" + cartId, null, 200).body());
        assertEquals(1, finalCart.get("items").size());
        assertEquals(2, finalCart.get("items").get(0).get("productId").intValue());
        assertEquals(150000, finalCart.get("total").intValue());
    }

    private HttpResponse<String> send(HttpClient client, String method, String path,
                                      String body, int expectedStatus) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/carts" + path))
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
