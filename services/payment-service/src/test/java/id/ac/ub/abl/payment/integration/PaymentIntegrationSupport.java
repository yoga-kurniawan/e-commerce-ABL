package id.ac.ub.abl.payment.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** Real application/socket, disposable database and consumer-owned HTTP Order stub. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "spring.http.serviceclient.orders.read-timeout=15s"})
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PaymentIntegrationSupport {
    protected static final String BASE = "/api/v1/payments";
    protected static final String TOKEN = "public-integration-test-token-00000000";
    @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    private static OrderStub stub;
    protected static synchronized OrderStub orders() {
        if (stub == null) { stub = new OrderStub(); }
        return stub;
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("clients.order-url", () -> orders().url());
    }
    @AfterAll static synchronized void closeStub() {
        if (stub != null) { stub.close(); stub = null; }
    }
    @Autowired protected RestTestClient http;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JsonMapper json;

    @BeforeEach void resetDatabaseAndOrderStub() throws Exception {
        // Refuse destructive fixture cleanup unless connected to this disposable container.
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(DATABASE.getJdbcUrl());
        }
        jdbc.execute("TRUNCATE payment_idempotency, payments, payment_order_guards RESTART IDENTITY CASCADE");
        orders().reset();
    }
    protected String request(String method, String path, String body, String key, boolean simulator, int status) {
        var request = http.method(HttpMethod.valueOf(method)).uri(path).contentType(MediaType.APPLICATION_JSON);
        if (key != null) { request.header("Idempotency-Key", key); }
        if (simulator) { request.header("X-Simulator-Token", TOKEN); }
        return request.body(body == null ? "" : body).exchange().expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class).returnResult().getResponseBody();
    }
    protected String create(String key, long order, String method, int status) {
        return request("POST", BASE, "{\"orderId\":" + order + ",\"method\":\"" + method + "\"}", key, false, status);
    }
    protected String result(long id, String status, int expected) {
        return request("POST", "/internal/dev/payments/" + id + "/result", "{\"status\":\"" + status + "\"}", null, true, expected);
    }
    protected JsonNode body(String raw) { return json.readTree(raw); }
    protected long id(String raw) { return body(raw).get("id").asLong(); }
    protected void error(String raw, String code) {
        assertThat(body(raw).size()).isEqualTo(2);
        assertThat(body(raw).get("code").asText()).isEqualTo(code);
        assertThat(body(raw).get("message").asText()).isNotBlank();
    }
    protected int count(String table) {
        if (!List.of("payments", "payment_idempotency").contains(table)) { throw new IllegalArgumentException("Unknown table"); }
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
    protected static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).as("controlled synchronization").isTrue(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    @FunctionalInterface protected interface Reply { void send(HttpExchange exchange) throws Exception; }
    protected static class OrderStub implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        public final ConcurrentLinkedQueue<String> requests = new ConcurrentLinkedQueue<>();
        public final AtomicReference<Reply> reply = new AtomicReference<>();
        OrderStub() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.setExecutor(executor);
                server.createContext("/", exchange -> {
                    requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                    try { reply.get().send(exchange); }
                    catch (Exception failure) { exchange.close(); }
                });
                reset(); server.start();
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
        public String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        public void reset() { requests.clear(); reply.set(this::success); }
        public void success(HttpExchange exchange) throws Exception {
            String path = exchange.getRequestURI().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            send(exchange, 200, "{\"id\":" + id + ",\"total\":350000.12500,\"status\":\"PENDING\"}");
        }
        public void send(HttpExchange exchange, int status, String body) throws Exception {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        }
        public void close() { server.stop(0); executor.shutdownNow(); }
    }
}
