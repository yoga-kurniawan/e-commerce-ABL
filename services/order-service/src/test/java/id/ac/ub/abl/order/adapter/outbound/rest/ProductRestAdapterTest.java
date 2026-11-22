package id.ac.ub.abl.order.adapter.outbound.rest;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import id.ac.ub.abl.order.adapter.outbound.rest.mapper.ProductRestMapper;
import id.ac.ub.abl.order.configuration.CorrelationHeaderPropagationConfiguration;
import id.ac.ub.abl.order.configuration.RestClientConfiguration;
import id.ac.ub.abl.order.domain.ProductSnapshot;
import id.ac.ub.abl.order.usecase.exception.ProductServiceUnavailableException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

/** Client Boot 4 @ImportHttpServices/@HttpExchange untuk Product Service dengan stub HTTP loopback. */
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
        context = new SpringApplicationBuilder(ClientTestConfiguration.class).web(WebApplicationType.NONE).run(
                "--clients.product-url=http://127.0.0.1:" + server.getAddress().getPort(),
                "--management.endpoint.health.group.readiness.include=readinessState",
                "--spring.main.banner-mode=off");
        adapter = context.getBean(ProductRestAdapter.class);
    }

    @AfterAll
    static void stop() {
        if (context != null) { context.close(); }
        if (server != null) { server.stop(0); }
        if (executor != null) { executor.shutdownNow(); }
    }

    @BeforeEach
    void reset() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
        reply(200, "application/json", """
                {"id": 1, "name": "Keyboard Mechanical", "price": 350000, "stock": 20}
                """);
    }

    @Test
    void findById_whenProductExists_returnsSnapshot() {
        ProductSnapshot product = adapter.findById(1L).orElseThrow();

        assertEquals("/api/v1/products/1", PATH.get());
        assertEquals("GET", METHOD.get());
        assertEquals("Keyboard Mechanical", product.name());
        assertEquals(new BigDecimal("350000"), product.price());
        assertEquals(20, product.stock());
    }

    @Test
    void findById_whenProductNotFoundOrInactive_returnsEmpty() {
        reply(404, "application/json", "{\"code\":\"NOT_FOUND\"}");

        assertTrue(adapter.findById(999L).isEmpty());
        assertEquals("/api/v1/products/999", PATH.get());
    }

    @Test
    void findById_whenServerError_throwsProductServiceUnavailable() {
        reply(500, "application/json", "{\"code\":\"INTERNAL_SERVER_ERROR\"}");

        assertThrows(ProductServiceUnavailableException.class, () -> adapter.findById(1L));
    }

    @Test
    void findAllByIds_withMultipleIds_executesInParallelAndPreservesOrder() {
        var arrived = new CountDownLatch(2);
        REPLY.set(exchange -> {
            String path = exchange.getRequestURI().getPath();
            arrived.countDown();
            assertTrue(arrived.await(5, TimeUnit.SECONDS), "Requests did not execute concurrently");
            String body = path.endsWith("/1")
                    ? "{\"id\": 1, \"name\": \"Keyboard\", \"price\": 350000, \"stock\": 20}"
                    : "{\"id\": 2, \"name\": \"Mouse\", \"price\": 150000, \"stock\": 10}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        Map<Long, Optional<ProductSnapshot>> result = adapter.findAllByIds(List.of(1L, 2L, 1L));

        assertEquals(List.of(1L, 2L), List.copyOf(result.keySet()));
        assertEquals("Keyboard", result.get(1L).orElseThrow().name());
        assertEquals("Mouse", result.get(2L).orElseThrow().name());
    }

    @Test
    void findAllByIds_whenOneProductFails_propagatesDeterministicException() {
        REPLY.set(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/2")) {
                byte[] bytes = "{\"code\":\"INTERNAL_SERVER_ERROR\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            byte[] bytes = "{\"id\": 1, \"name\": \"Keyboard\", \"price\": 350000, \"stock\": 20}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        assertThrows(ProductServiceUnavailableException.class, () -> adapter.findAllByIds(List.of(1L, 2L)));
    }

    @Test
    void productLookupExecutor_propagatesRequestAttributesAndMdcToWorkerThreads() throws Exception {
        ThreadPoolTaskExecutor pool = context.getBean("productLookupExecutor", ThreadPoolTaskExecutor.class);
        var request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("x-request-id", "req-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("requestId", "req-123");

        AtomicReference<String> seenHeader = new AtomicReference<>();
        AtomicReference<String> seenMdc = new AtomicReference<>();
        AtomicReference<String> seenThread = new AtomicReference<>();
        var done = new CountDownLatch(1);

        pool.execute(() -> {
            try {
                var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                seenHeader.set(attrs == null ? null : attrs.getRequest().getHeader("x-request-id"));
                seenMdc.set(MDC.get("requestId"));
                seenThread.set(Thread.currentThread().getName());
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals("req-123", seenHeader.get());
        assertEquals("req-123", seenMdc.get());
        assertTrue(seenThread.get().startsWith("product-lookup-"));
    }

    @Test
    void findById_propagatesCorrelationHeaderAndTracing() {
        var request = new MockHttpServletRequest();
        request.addHeader("x-request-id", "order-req-456");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            adapter.findById(1L);
            assertEquals("order-req-456", HEADERS.get().getFirst("x-request-id"));
            assertNotNull(HEADERS.get().getFirst("traceparent"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void findAllByIds_propagatesParentTraceIdAndCorrelationHeaderAcrossParallelRequests() {
        Tracer tracer = context.getBean(Tracer.class);
        Span parentSpan = tracer.nextSpan().name("order-parallel-lookup-parent").start();
        String expectedTraceId = parentSpan.context().traceId();

        var request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("x-request-id", "order-parallel-789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var arrived = new CountDownLatch(2);
        Map<String, String> capturedRequestIds = new ConcurrentHashMap<>();
        Map<String, String> capturedTraceParents = new ConcurrentHashMap<>();
        REPLY.set(exchange -> {
            String path = exchange.getRequestURI().getPath();
            String reqId = exchange.getRequestHeaders().getFirst("x-request-id");
            String traceparent = exchange.getRequestHeaders().getFirst("traceparent");
            if (reqId != null) { capturedRequestIds.put(path, reqId); }
            if (traceparent != null) { capturedTraceParents.put(path, traceparent); }
            arrived.countDown();
            assertTrue(arrived.await(5, TimeUnit.SECONDS), "Requests did not execute concurrently");
            String body = path.endsWith("/1")
                    ? "{\"id\": 1, \"name\": \"Keyboard\", \"price\": 350000, \"stock\": 20}"
                    : "{\"id\": 2, \"name\": \"Mouse\", \"price\": 150000, \"stock\": 10}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        try (Tracer.SpanInScope ignored = tracer.withSpan(parentSpan)) {
            Map<Long, Optional<ProductSnapshot>> result = adapter.findAllByIds(List.of(1L, 2L));
            assertEquals(2, result.size());
        } finally {
            parentSpan.end();
            RequestContextHolder.resetRequestAttributes();
        }

        assertEquals("order-parallel-789", capturedRequestIds.get("/api/v1/products/1"));
        assertEquals("order-parallel-789", capturedRequestIds.get("/api/v1/products/2"));

        String tp1 = capturedTraceParents.get("/api/v1/products/1");
        String tp2 = capturedTraceParents.get("/api/v1/products/2");
        assertNotNull(tp1, "traceparent missing for product 1");
        assertNotNull(tp2, "traceparent missing for product 2");
        assertEquals(expectedTraceId, tp1.split("-")[1]);
        assertEquals(expectedTraceId, tp2.split("-")[1]);
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
    @Import({RestClientConfiguration.class, CorrelationHeaderPropagationConfiguration.class, ProductRestAdapter.class, ProductRestMapper.class})
    static class ClientTestConfiguration {
    }
}
