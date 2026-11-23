package id.ac.ub.abl.order.adapter.outbound.rest;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import id.ac.ub.abl.order.adapter.outbound.rest.mapper.CartRestMapper;
import id.ac.ub.abl.order.configuration.RestClientConfiguration;
import id.ac.ub.abl.order.domain.CartSnapshot;
import id.ac.ub.abl.order.usecase.exception.CartServiceUnavailableException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

/** Client Boot 4 @ImportHttpServices/@HttpExchange untuk Cart Service v2 dengan stub HTTP loopback. */
class CartRestAdapterTest {
    interface Reply { void send(HttpExchange exchange) throws Exception; }

    static final AtomicReference<Reply> REPLY = new AtomicReference<>();
    static final AtomicReference<String> PATH = new AtomicReference<>();
    static final AtomicReference<String> METHOD = new AtomicReference<>();
    static HttpServer server;
    static ExecutorService executor;
    static ConfigurableApplicationContext context;
    static CartRestAdapter adapter;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            PATH.set(exchange.getRequestURI().getPath());
            METHOD.set(exchange.getRequestMethod());
            try { REPLY.get().send(exchange); } catch (Exception ignored) { exchange.close(); }
        });
        server.start();
        context = new SpringApplicationBuilder(ClientTestConfiguration.class).web(WebApplicationType.NONE).run(
                "--clients.cart-url=http://127.0.0.1:" + server.getAddress().getPort(),
                "--management.endpoint.health.group.readiness.include=readinessState",
                "--spring.main.banner-mode=off");
        adapter = context.getBean(CartRestAdapter.class);
    }

    @AfterAll
    static void stop() {
        if (context != null) { context.close(); }
        if (server != null) { server.stop(0); }
        if (executor != null) { executor.shutdownNow(); }
    }

    @BeforeEach
    void reset() {
        reply(200, "application/json", """
                {
                    "id": 10,
                    "customerId": 101,
                    "items": [
                        {"productId": 1, "quantity": 2, "unitPrice": 350000.125, "subtotal": 700000.250}
                    ],
                    "total": 700000.250
                }
                """);
    }

    @Test
    void findById_whenCartV2Exists_returnsSnapshotWithExplicitUnitPrice() {
        Optional<CartSnapshot> result = adapter.findById(10L);

        CartSnapshot cart = result.orElseThrow();
        assertEquals("/api/v2/carts/10", PATH.get());
        assertEquals("GET", METHOD.get());
        assertEquals(10L, cart.id());
        assertEquals(101L, cart.customerId());
        assertEquals(1, cart.items().size());
        assertEquals(2, cart.items().get(0).quantity());
        assertEquals(0, new BigDecimal("350000.125").compareTo(cart.items().get(0).unitPrice()));
        assertEquals(0, new BigDecimal("700000.250").compareTo(cart.items().get(0).subtotal()));
    }

    @Test
    void findById_whenUnitPriceMissing_fallsBackToExactDivision() {
        reply(200, "application/json", """
                {
                    "id": 10,
                    "customerId": 101,
                    "items": [
                        {"productId": 1, "quantity": 2, "subtotal": 700000.250}
                    ],
                    "total": 700000.250
                }
                """);

        CartSnapshot cart = adapter.findById(10L).orElseThrow();
        assertEquals(0, new BigDecimal("350000.125").compareTo(cart.items().get(0).unitPrice()));
    }

    @Test
    void findById_whenUnitPriceMissingAndNonTerminatingDivision_throwsCartServiceUnavailable() {
        reply(200, "application/json", """
                {
                    "id": 10,
                    "customerId": 101,
                    "items": [
                        {"productId": 1, "quantity": 3, "subtotal": 100}
                    ],
                    "total": 100
                }
                """);

        assertThrows(CartServiceUnavailableException.class, () -> adapter.findById(10L));
    }

    @Test
    void findById_whenCartIsEmpty_returnsEmptySnapshot() {
        reply(200, "application/json", "{\"id\":10,\"customerId\":101,\"items\":[],\"total\":0}");

        assertTrue(adapter.findById(10L).orElseThrow().isEmpty());
    }

    @Test
    void findById_whenCartNotFound_returnsEmpty() {
        reply(404, "application/json", "{\"code\":\"NOT_FOUND\"}");

        assertTrue(adapter.findById(999L).isEmpty());
        assertEquals("/api/v2/carts/999", PATH.get());
    }

    @Test
    void findById_whenServerError_throwsCartServiceUnavailable() {
        reply(500, "application/json", "{\"code\":\"INTERNAL_SERVER_ERROR\"}");

        assertThrows(CartServiceUnavailableException.class, () -> adapter.findById(10L));
    }

    @Test
    void findById_whenResponseInvalid_throwsCartServiceUnavailable() {
        reply(200, "application/json", "{\"id\":10,\"items\":[]}");

        assertThrows(CartServiceUnavailableException.class, () -> adapter.findById(10L));
    }

    static void reply(int status, String contentType, String body) {
        REPLY.set(exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({RestClientConfiguration.class, CartRestAdapter.class, CartRestMapper.class})
    static class ClientTestConfiguration {
    }
}
