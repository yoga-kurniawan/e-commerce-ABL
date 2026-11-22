package id.ac.ub.abl.cart.adapter.outbound.rest;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import id.ac.ub.abl.cart.adapter.outbound.rest.mapper.ProductRestMapper;
import id.ac.ub.abl.cart.configuration.CorrelationHeaderPropagationConfiguration;
import id.ac.ub.abl.cart.configuration.ProductClientConfiguration;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.usecase.exception.ProductServiceUnavailableException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Client Boot 4 @ImportHttpServices/@HttpExchange nyata dengan stub HTTP loopback. */
class ProductRestAdapterTest {
    interface Reply { void send(HttpExchange exchange) throws Exception; }

    static final AtomicReference<Reply> REPLY = new AtomicReference<>();
    static final AtomicReference<String> PATH = new AtomicReference<>();
    static final AtomicReference<String> METHOD = new AtomicReference<>();
    static final AtomicReference<com.sun.net.httpserver.Headers> HEADERS = new AtomicReference<>();
    static HttpServer server;
    static ExecutorService executor;
    static ConfigurableApplicationContext context;
    static ProductRestAdapter adapter;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            PATH.set(exchange.getRequestURI().getPath());
            METHOD.set(exchange.getRequestMethod());
            HEADERS.set(exchange.getRequestHeaders());
            try { REPLY.get().send(exchange); } catch (Exception ignored) { exchange.close(); }
        });
        server.start();
        context = startContext(server.getAddress().getPort(), "4s");
        adapter = context.getBean(ProductRestAdapter.class);
    }

    static ConfigurableApplicationContext startContext(int port, String readTimeout) {
        return new SpringApplicationBuilder(ClientTestConfiguration.class).web(WebApplicationType.NONE).run(
                "--clients.product-url=http://127.0.0.1:" + port,
                "--spring.http.serviceclient.products.read-timeout=" + readTimeout,
                "--management.endpoint.health.group.readiness.include=readinessState",
                "--spring.main.banner-mode=off");
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
                {"id": 1, "name": "Keyboard Mechanical", "price": 350000, "stock": 20}
                """);
    }

    @Test
    void findById_whenProductExists_returnsSnapshotAndUsesConfiguredGroupTimeouts() {
        Optional<ProductSnapshot> result = adapter.findById(1L);

        assertThat(result).isPresent();
        ProductSnapshot snapshot = result.get();
        assertThat(snapshot.id()).isEqualTo(1L);
        assertThat(snapshot.name()).isEqualTo("Keyboard Mechanical");
        assertThat(snapshot.price()).isEqualByComparingTo(new BigDecimal("350000"));
        assertThat(snapshot.stock()).isEqualTo(20);
        assertThat(PATH.get()).isEqualTo("/api/v1/products/1");
        assertThat(METHOD.get()).isEqualTo("GET");
        assertThat(context.getEnvironment().getProperty("spring.http.serviceclient.products.connect-timeout"))
                .isEqualTo("2s");
    }

    @Test
    void findById_whenProductNotFound_returnsEmpty() {
        reply(404, "application/json", "{\"code\":\"NOT_FOUND\",\"message\":\"Produk tidak ditemukan\"}");

        assertThat(adapter.findById(999L)).isEmpty();
        assertThat(PATH.get()).isEqualTo("/api/v1/products/999");
    }

    @Test
    void findById_whenServerErrorOrRedirect_throwsProductServiceUnavailableException() {
        reply(500, "application/json", "{\"code\":\"INTERNAL_SERVER_ERROR\"}");
        assertThatThrownBy(() -> adapter.findById(1L))
                .isInstanceOf(ProductServiceUnavailableException.class);

        reply(302, "application/json", "{}");
        assertThatThrownBy(() -> adapter.findById(1L))
                .isInstanceOf(ProductServiceUnavailableException.class);
    }

    @Test
    void findById_whenReadTimesOut_throwsProductServiceUnavailableException() {
        var release = new CountDownLatch(1);
        REPLY.set(exchange -> {
            release.await(5, TimeUnit.SECONDS);
            exchange.close();
        });
        try (var timed = startContext(server.getAddress().getPort(), "200ms")) {
            assertThatThrownBy(() -> timed.getBean(ProductRestAdapter.class).findById(1L))
                    .isInstanceOf(ProductServiceUnavailableException.class);
        } finally {
            release.countDown();
        }
    }

    @Test
    void findById_propagatesCorrelationHeaderAndTracing() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("x-request-id", "cart-req-123");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            adapter.findById(1L);
            assertThat(HEADERS.get().getFirst("x-request-id")).isEqualTo("cart-req-123");
            assertThat(HEADERS.get().getFirst("traceparent")).isNotBlank();
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    private static void reply(int status, String contentType, String body) {
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
    @Import({ProductClientConfiguration.class, CorrelationHeaderPropagationConfiguration.class, ProductRestAdapter.class, ProductRestMapper.class})
    static class ClientTestConfiguration { }
}
