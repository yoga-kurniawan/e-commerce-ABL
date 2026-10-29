package id.ac.ub.abl.payment.adapter.outbound.rest;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import id.ac.ub.abl.payment.adapter.outbound.rest.mapper.OrderSnapshotMapper;
import id.ac.ub.abl.payment.domain.OrderSnapshot;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.*;

/** Client Boot/RestClient/proxy nyata dengan stub HTTP loopback, tanpa database atau Order nyata. */
class OrderRestAdapterTest {
    interface Reply { void send(HttpExchange exchange) throws Exception; }
    static final AtomicReference<Reply> REPLY = new AtomicReference<>();
    static final AtomicReference<String> PATH = new AtomicReference<>();
    static final AtomicReference<String> METHOD = new AtomicReference<>();
    static HttpServer server;
    static ExecutorService executor;
    static ConfigurableApplicationContext context;
    static OrderRestAdapter adapter;

    @BeforeAll static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            PATH.set(exchange.getRequestURI().getPath()); METHOD.set(exchange.getRequestMethod());
            try { REPLY.get().send(exchange); } catch (Exception ignored) { exchange.close(); }
        });
        server.start();
        context = startContext(server.getAddress().getPort());
        adapter = context.getBean(OrderRestAdapter.class);
    }
    static ConfigurableApplicationContext startContext(int port) {
        return startContext(port, "5s");
    }
    static ConfigurableApplicationContext startContext(int port, String readTimeout) {
        return new SpringApplicationBuilder(ClientTestConfiguration.class).web(WebApplicationType.NONE).run(
                "--clients.order-url=http://127.0.0.1:" + port,
                "--spring.http.serviceclient.orders.read-timeout=" + readTimeout, "--spring.main.banner-mode=off");
    }
    @AfterAll static void stop() {
        if (context != null) { context.close(); }
        if (server != null) { server.stop(0); }
        if (executor != null) { executor.shutdownNow(); }
    }
    @BeforeEach void reset() { reply(200, "application/json", "{\"id\":501,\"total\":350000.12500,\"status\":\"PENDING\",\"customerId\":101}"); }
    @Test void lookup_whenValid_shouldUseConfiguredHostPathAndExactTotal() {
        var snapshot = adapter.findById(501L).orElseThrow();
        assertThat(snapshot.total()).isEqualByComparingTo("350000.12500");
        assertThat(snapshot.status()).isEqualTo(OrderSnapshot.Status.PENDING);
        assertThat(PATH.get()).isEqualTo("/api/v1/orders/501");
        assertThat(METHOD.get()).isEqualTo("GET");
        assertThat(context.getEnvironment().getProperty("spring.http.serviceclient.orders.connect-timeout")).isEqualTo("2s");
    }
    @ParameterizedTest @ValueSource(strings = {"CONFIRMED", "CANCELLED"})
    void lookup_whenOrderNotPayable_shouldStillReturnSnapshot(String status) {
        reply(200, "application/json", "{\"id\":501,\"total\":0,\"status\":\"" + status + "\"}");
        assertThat(adapter.findById(501L).orElseThrow().status()).isEqualTo(OrderSnapshot.Status.valueOf(status));
    }
    @Test void lookup_whenNotFound_shouldReturnEmpty() {
        reply(404, "application/json", "{\"code\":\"NOT_FOUND\",\"message\":\"missing\"}");
        assertThat(adapter.findById(501L)).isEmpty();
    }
    @ParameterizedTest @ValueSource(ints = {400, 401, 403, 409, 429, 500, 502, 503, 504})
    void lookup_whenRemoteError_shouldNeverTreatItAsMissing(int status) {
        reply(status, "application/json", "{}"); assertCode("ORDER_UPSTREAM_ERROR");
    }
    @ParameterizedTest @ValueSource(strings = {
            "", "not json", "null", "[]", "{}", "{\"id\":502,\"total\":1,\"status\":\"PENDING\"}",
            "{\"id\":501,\"total\":-1,\"status\":\"PENDING\"}",
            "{\"id\":501,\"total\":null,\"status\":\"PENDING\"}",
            "{\"id\":501,\"total\":\"1.5\",\"status\":\"PENDING\"}",
            "{\"id\":\"501\",\"total\":1,\"status\":\"PENDING\"}",
            "{\"id\":501.0,\"total\":1,\"status\":\"PENDING\"}",
            "{\"id\":9223372036854775808,\"total\":1,\"status\":\"PENDING\"}",
            "{\"id\":501,\"total\":1,\"status\":\"UNKNOWN\"}",
            "{\"id\":501,\"total\":NaN,\"status\":\"PENDING\"}",
            "{\"id\":501,\"id\":502,\"total\":1,\"status\":\"PENDING\"}",
            "{\"id\":501,\"total\":1,\"status\":\"PENDING\"} {}"})
    void lookup_whenMalformed_shouldReportUpstreamError(String body) {
        reply(200, "application/json", body); assertCode("ORDER_UPSTREAM_ERROR");
    }
    @ParameterizedTest @ValueSource(ints = {201, 204, 302})
    void lookup_whenUnexpectedSuccessOrRedirect_shouldReject(int status) {
        reply(status, "application/json", "{}"); assertCode("ORDER_UPSTREAM_ERROR");
    }
    @ParameterizedTest @ValueSource(strings = {"text/html", "invalid-type"})
    void lookup_whenWrongContentType_shouldReject(String contentType) {
        reply(200, contentType, "{}"); assertCode("ORDER_UPSTREAM_ERROR");
    }
    @Test void lookup_whenPreciseLargeTotal_shouldNotConvertThroughDouble() {
        reply(200, "application/json", "{\"id\":501,\"total\":123456789012345678901234567890.123456789,\"status\":\"PENDING\"}");
        assertThat(adapter.findById(501L).orElseThrow().total()).isEqualByComparingTo("123456789012345678901234567890.123456789");
    }
    @Test void lookup_whenReadStalls_shouldUseConfiguredTimeout() {
        var release = new CountDownLatch(1);
        REPLY.set(exchange -> { release.await(5, TimeUnit.SECONDS); exchange.close(); });
        try (var timed = startContext(server.getAddress().getPort(), "200ms")) {
            assertThatThrownBy(() -> timed.getBean(OrderRestAdapter.class).findById(501L))
                    .satisfies(error -> assertCode(error, "ORDER_TIMEOUT"));
        } finally { release.countDown(); }
    }
    @Test void lookup_whenConnectionRefused_shouldReportUnavailable() throws Exception {
        int unusedPort;
        try (var socket = new ServerSocket(0)) { unusedPort = socket.getLocalPort(); }
        try (var refused = startContext(unusedPort)) {
            assertThatThrownBy(() -> refused.getBean(OrderRestAdapter.class).findById(501L))
                    .satisfies(error -> assertCode(error, "DEPENDENCY_UNAVAILABLE"));
        }
    }
    void reply(int status, String type, String body) {
        REPLY.set(exchange -> {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
            if (status != 204) { exchange.getResponseBody().write(bytes); }
            exchange.close();
        });
    }
    void assertCode(String code) { assertThatThrownBy(() -> adapter.findById(501L)).satisfies(error -> assertCode(error, code)); }
    static void assertCode(Throwable error, String code) {
        assertThat(error).isInstanceOfSatisfying(PaymentOperationException.class, failure -> assertThat(failure.getCode()).isEqualTo(code));
    }
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({OrderClientConfiguration.class, OrderRestAdapter.class, OrderSnapshotMapper.class})
    static class ClientTestConfiguration { }
}
