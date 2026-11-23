package id.ac.ub.abl.order.adapter.outbound.rest;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import id.ac.ub.abl.order.adapter.outbound.rest.mapper.PaymentRestMapper;
import id.ac.ub.abl.order.configuration.RestClientConfiguration;
import id.ac.ub.abl.order.domain.PaymentSnapshot;
import id.ac.ub.abl.order.usecase.exception.PaymentServiceUnavailableException;
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

/** Client Boot 4 @ImportHttpServices/@HttpExchange untuk Payment Service v2 dengan stub HTTP loopback. */
class PaymentRestAdapterTest {
    interface Reply { void send(HttpExchange exchange) throws Exception; }

    static final AtomicReference<Reply> REPLY = new AtomicReference<>();
    static final AtomicReference<String> URI_STR = new AtomicReference<>();
    static final AtomicReference<String> METHOD = new AtomicReference<>();
    static HttpServer server;
    static ExecutorService executor;
    static ConfigurableApplicationContext context;
    static PaymentRestAdapter adapter;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            URI_STR.set(exchange.getRequestURI().toString());
            METHOD.set(exchange.getRequestMethod());
            try { REPLY.get().send(exchange); } catch (Exception ignored) { exchange.close(); }
        });
        server.start();
        context = new SpringApplicationBuilder(ClientTestConfiguration.class).web(WebApplicationType.NONE).run(
                "--clients.payment-url=http://127.0.0.1:" + server.getAddress().getPort(),
                "--management.endpoint.health.group.readiness.include=readinessState",
                "--spring.main.banner-mode=off");
        adapter = context.getBean(PaymentRestAdapter.class);
    }

    @AfterAll
    static void stop() {
        if (context != null) { context.close(); }
        if (server != null) { server.stop(0); }
        if (executor != null) { executor.shutdownNow(); }
    }

    @BeforeEach
    void reset() {
        reply(200, "application/json", "[]");
    }

    @Test
    void findByOrderId_mapsPaymentHistoryAndIgnoresOtherFields() {
        reply(200, "application/json", """
                [
                  {"id": 9001, "orderId": 501, "method": "TRANSFER", "status": "FAILED", "amount": 700000},
                  {"id": 9002, "orderId": 501, "method": "E_WALLET", "status": "PENDING", "amount": 700000,
                   "transactionReference": "TRX-DEMO-001"}
                ]
                """);

        List<PaymentSnapshot> payments = adapter.findByOrderId(501L);

        assertEquals("/api/v2/payments?orderId=501", URI_STR.get());
        assertEquals("GET", METHOD.get());
        assertEquals(List.of(
                new PaymentSnapshot(9001L, 501L, PaymentSnapshot.Status.FAILED),
                new PaymentSnapshot(9002L, 501L, PaymentSnapshot.Status.PENDING)), payments);
    }

    @Test
    void findByOrderId_withoutPayments_returnsEmptyList() {
        assertTrue(adapter.findByOrderId(501L).isEmpty());
    }

    @Test
    void findByOrderId_whenServerErrorOrNotFound_throwsUnavailable() {
        reply(500, "application/json", "{\"code\":\"INTERNAL_SERVER_ERROR\"}");
        assertThrows(PaymentServiceUnavailableException.class, () -> adapter.findByOrderId(501L));

        reply(404, "application/json", "{\"code\":\"NOT_FOUND\"}");
        assertThrows(PaymentServiceUnavailableException.class, () -> adapter.findByOrderId(501L));
    }

    @Test
    void findByOrderId_withInvalidResponse_throwsUnavailable() {
        List<String> invalidBodies = List.of(
                "{\"id\":9001,\"orderId\":501,\"status\":\"PENDING\"}",
                "[{\"id\":9001,\"orderId\":501,\"status\":\"REFUNDED\"}]",
                "[{\"id\":9001,\"orderId\":777,\"status\":\"FAILED\"}]",
                "[{\"id\":9001,\"orderId\":501}]",
                "[{\"orderId\":501,\"status\":\"FAILED\"}]");
        for (String body : invalidBodies) {
            reply(200, "application/json", body);
            assertThrows(PaymentServiceUnavailableException.class, () -> adapter.findByOrderId(501L), body);
        }
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
    @Import({RestClientConfiguration.class, PaymentRestAdapter.class, PaymentRestMapper.class})
    static class ClientTestConfiguration {
    }
}
