package id.ac.ub.abl.payment.integration;

import java.util.concurrent.*;
import id.ac.ub.abl.payment.adapter.inbound.rest.PaymentCreationResponseEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = {"payment.simulator.enabled=true", "payment.simulator.token=public-integration-test-token-00000000"})
@Import(PaymentFlowIntegrationTest.ResponseFaultConfiguration.class)
class PaymentFlowIntegrationTest extends PaymentIntegrationSupport {
    @MockitoSpyBean PaymentCreationResponseEncoder encoder;

    @ParameterizedTest @ValueSource(strings = {"SUCCESS", "FAILED"})
    void payment_whenCreatedPatchedAndFinalized_shouldPreserveAmountReplayAndTerminalRules(String terminal) {
        String created = create("lifecycle", 501, "COD", 201);
        long id = id(created);
        assertThat(created).contains("\"amount\":350000.12500");
        assertThat(body(created).get("status").asText()).isEqualTo("PENDING");
        assertThat(body(created).has("transactionReference")).isFalse();
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE id=?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("350000.12500");
        String patched = request("PATCH", BASE + "/" + id, "{\"transactionReference\":\"  ref  \"}", null, false, 200);
        assertThat(body(patched).get("transactionReference").asText()).isEqualTo("  ref  ");
        assertThat(body(patched).get("status").asText()).isEqualTo("PENDING");
        String finalized = result(id, terminal, 200);
        assertThat(finalized).contains("\"amount\":350000.12500");
        assertThat(body(finalized).get("status").asText()).isEqualTo(terminal);
        assertThat(body(finalized).get("transactionReference").asText()).isEqualTo("  ref  ");
        var metadata = jdbc.queryForMap("SELECT version,created_at,updated_at FROM payments WHERE id=?", id);
        assertThat(result(id, terminal, 200)).isEqualTo(finalized);
        assertThat(jdbc.queryForMap("SELECT version,created_at,updated_at FROM payments WHERE id=?", id)).isEqualTo(metadata);
        error(result(id, terminal.equals("SUCCESS") ? "FAILED" : "SUCCESS", 409), "PAYMENT_RESULT_CONFLICT");
        error(request("PATCH", BASE + "/" + id, "{\"transactionReference\":\"other\"}", null, false, 409), "PAYMENT_NOT_MODIFIABLE");
        assertThat(request("GET", BASE + "/" + id, null, null, false, 200)).isEqualTo(finalized);
        int lookups = orders().requests.size();
        orders().reply.set(exchange -> orders().send(exchange, 500, "{}"));
        assertThat(create("lifecycle", 501, "COD", 201)).isEqualTo(created);
        assertThat(orders().requests).hasSize(lookups);
        orders().reply.set(orders()::success);
        if (terminal.equals("FAILED")) {
            String next = create("next-attempt", 501, "TRANSFER", 201);
            assertThat(id(next)).isNotEqualTo(id);
            assertThat(body(next).get("status").asText()).isEqualTo("PENDING");
            assertThat(count("payments")).isEqualTo(2);
        } else {
            error(create("next-attempt", 501, "TRANSFER", 409), "ORDER_ALREADY_PAID");
            assertThat(count("payments")).isEqualTo(1);
        }
        assertThat(orders().requests).allMatch(value -> value.equals("GET /api/v1/orders/501"));
    }
    @Test void create_whenKeyMissingOrBodyInvalid_shouldNotClaimOrLookupOrder() {
        error(create(null, 501, "COD", 400), "BAD_REQUEST");
        error(request("POST", BASE, "{\"orderId\":501,\"method\":\"COD\",\"amount\":1}", "invalid", false, 400), "BAD_REQUEST");
        assertThat(count("payments")).isZero(); assertThat(count("payment_idempotency")).isZero();
        assertThat(orders().requests).isEmpty();
    }
    @ParameterizedTest @CsvSource({"502,COD", "501,TRANSFER"})
    void create_whenPayloadDiffers_shouldRejectWithoutAnotherLookup(long order, String method) {
        create("same", 501, "COD", 201);
        error(create("same", order, method, 409), "IDEMPOTENCY_KEY_REUSED");
        assertThat(orders().requests).hasSize(1); assertThat(count("payments")).isEqualTo(1);
        error(create("different", 501, "COD", 409), "PAYMENT_ALREADY_PENDING");
        assertThat(count("payment_idempotency")).isEqualTo(1);
    }
    @Test void create_whenSameKeyInProgress_shouldRejectThenReplayAfterCompletion() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        orders().reply.set(exchange -> { entered.countDown(); await(release); orders().success(exchange); });
        try {
            var first = executor.submit(() -> create("concurrent", 501, "COD", 201));
            await(entered);
            assertThat(jdbc.queryForObject("SELECT state FROM payment_idempotency WHERE key='concurrent'", String.class)).isEqualTo("PROCESSING");
            error(create("concurrent", 501, "COD", 409), "REQUEST_IN_PROGRESS");
            error(create("concurrent", 501, "TRANSFER", 409), "IDEMPOTENCY_KEY_REUSED");
            assertThat(count("payments")).isZero(); assertThat(orders().requests).hasSize(1);
            release.countDown(); String created = first.get(10, TimeUnit.SECONDS);
            assertThat(create("concurrent", 501, "COD", 201)).isEqualTo(created);
            assertThat(count("payments")).isEqualTo(1);
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void create_whenDifferentKeysRaceForSameOrder_shouldPersistOneAttempt() throws Exception {
        var entered = new CountDownLatch(2); var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        orders().reply.set(exchange -> { entered.countDown(); await(release); orders().success(exchange); });
        try {
            Callable<Integer> first = () -> raceCreate("race-a");
            var a = executor.submit(first); var b = executor.submit(() -> raceCreate("race-b"));
            await(entered); release.countDown();
            assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
            assertThat(count("payments")).isEqualTo(1); assertThat(count("payment_idempotency")).isEqualTo(1);
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    private int raceCreate(String key) {
        var response = http.post().uri(BASE).header("Idempotency-Key", key).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body("{\"orderId\":501,\"method\":\"COD\"}").exchange().expectBody(String.class).returnResult();
        int status = response.getStatus().value();
        if (status == 409) { assertThat(body(response.getResponseBody()).get("code").asText()).isIn("PAYMENT_ALREADY_PENDING", "PAYMENT_OPERATION_BUSY"); }
        return status;
    }
    @ParameterizedTest @CsvSource({"404,404,ORDER_NOT_FOUND", "500,502,ORDER_UPSTREAM_ERROR"})
    void create_whenOrderFailsBeforeCommit_shouldReleaseClaimAndAllowSameKeyRetry(int upstream, int status, String code) {
        orders().reply.set(exchange -> orders().send(exchange, upstream, "{}"));
        error(create("retry-order", 501, "COD", status), code);
        assertThat(count("payments")).isZero(); assertThat(count("payment_idempotency")).isZero();
        orders().reply.set(orders()::success);
        create("retry-order", 501, "COD", 201); assertThat(count("payments")).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings = {"CONFIRMED", "CANCELLED"})
    void create_whenOrderNotPayable_shouldRejectAndReleaseClaim(String status) {
        orders().reply.set(exchange -> orders().send(exchange, 200, "{\"id\":501,\"total\":1,\"status\":\"" + status + "\"}"));
        error(create("not-payable", 501, "COD", 409), "ORDER_NOT_PAYABLE");
        assertThat(count("payments")).isZero(); assertThat(count("payment_idempotency")).isZero();
    }
    @Test void create_whenEncoderFailsAfterInsert_shouldRollbackThenRetryWithProductionEncoder() {
        doThrow(new IllegalStateException("test-only encoder failure")).doCallRealMethod().when(encoder).encode(any());
        error(create("rollback", 501, "COD", 500), "INTERNAL_SERVER_ERROR");
        assertThat(count("payments")).isZero(); assertThat(count("payment_idempotency")).isZero();
        String created = create("rollback", 501, "COD", 201);
        assertThat(created).contains("\"amount\":350000.12500"); assertThat(count("payments")).isEqualTo(1);
    }
    @Test void create_whenResponseFailsAfterCommit_shouldReplayOriginalStoredBodyWithoutDuplicate() {
        var failed = http.post().uri(BASE).header("Idempotency-Key", "lost-response").header("X-Test-Fail-Response", "true")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body("{\"orderId\":501,\"method\":\"COD\"}")
                .exchange().expectStatus().isEqualTo(503).expectBody(String.class).returnResult();
        error(failed.getResponseBody(), "TEST_RESPONSE_FAILURE");
        assertThat(count("payments")).isEqualTo(1);
        String stored = jdbc.queryForObject("SELECT response_body FROM payment_idempotency WHERE key='lost-response' AND state='COMPLETED'", String.class);
        assertThat(create("lost-response", 501, "COD", 201)).isEqualTo(stored);
        assertThat(count("payments")).isEqualTo(1); assertThat(orders().requests).hasSize(1);
    }
    /** Test-only fault at response delivery, after the real use case and transaction finish. */
    @TestConfiguration(proxyBeanMethods = false) static class ResponseFaultConfiguration {
        @Bean FilterRegistrationBean<OncePerRequestFilter> responseFault() {
            var filter = new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws java.io.IOException, ServletException {
                    if (!"true".equals(request.getHeader("X-Test-Fail-Response"))) { chain.doFilter(request, response); return; }
                    var buffered = new ContentCachingResponseWrapper(response);
                    chain.doFilter(request, buffered);
                    if (buffered.getStatus() != 201) { buffered.copyBodyToResponse(); return; }
                    response.setStatus(503); response.setContentType("application/json");
                    response.getOutputStream().write("{\"code\":\"TEST_RESPONSE_FAILURE\",\"message\":\"Injected after commit\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            };
            return new FilterRegistrationBean<>(filter);
        }
    }
}
