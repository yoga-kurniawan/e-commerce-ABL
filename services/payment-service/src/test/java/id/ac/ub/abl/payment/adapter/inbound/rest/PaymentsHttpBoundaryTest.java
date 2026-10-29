package id.ac.ub.abl.payment.adapter.inbound.rest;

import java.math.BigDecimal;
import id.ac.ub.abl.payment.adapter.inbound.rest.mapper.PaymentHttpMapper;
import id.ac.ub.abl.payment.adapter.inbound.rest.simulator.*;
import id.ac.ub.abl.payment.domain.*;
import id.ac.ub.abl.payment.usecase.command.*;
import id.ac.ub.abl.payment.usecase.model.*;
import id.ac.ub.abl.payment.usecase.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PaymentsApiController.class, DevelopmentPaymentResultController.class}, properties = {
        "payment.simulator.enabled=true", "server.address=127.0.0.1", "payment.simulator.token=test-only-simulator-token-000000000"})
@ActiveProfiles("test")
@Import({PaymentHttpMapper.class, PaymentJsonConfiguration.class, PaymentCreationResponseEncoder.class, PaymentSimulatorConfiguration.class})
class PaymentsHttpBoundaryTest {
    static final String BASE = "/api/v1/payments";
    // Fixture test publik; bukan secret environment nyata.
    static final String TOKEN = "test-only-simulator-token-000000000";
    @Autowired MockMvc http;
    @Autowired PaymentCreationResponseEncoder encoder;
    @MockitoBean CreatePaymentUseCase create;
    @MockitoBean GetPaymentUseCase get;
    @MockitoBean PatchPaymentUseCase patch;
    @MockitoBean ApplyPaymentResultUseCase apply;
    Payment payment(PaymentStatus status, String reference) {
        return Payment.restore(9001L, 501L, PaymentMethod.COD, new BigDecimal("350000.12500"), status, reference);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void create_whenNewOrReplayed_shouldSendStoredBodyExactly(boolean replay) throws Exception {
        var payment = payment(PaymentStatus.PENDING, null);
        var response = encoder.encode(payment);
        when(create.execute(any())).thenReturn(replay ? new CreatePaymentResult.Replayed(response) : new CreatePaymentResult.Created(payment, response));
        http.perform(post(BASE).header("Idempotency-Key", "Key-1").contentType("application/json")
                .content("{\"method\":\"COD\",\"orderId\":501}"))
                .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(response.body())).andExpect(jsonPath("$.transactionReference").doesNotExist());
        assertThat(response.body()).contains("\"amount\":350000.12500").doesNotContain("version", "createdAt", "data");
        verify(create).execute(new CreatePaymentCommand(501L, PaymentMethod.COD, new IdempotencyKey("Key-1")));
    }
    @Test void getAndPatch_whenValid_shouldReturnPublicDtoAndExactReference() throws Exception {
        when(get.execute(9001L)).thenReturn(payment(PaymentStatus.PENDING, null));
        when(patch.execute(any())).thenReturn(payment(PaymentStatus.PENDING, "  ref  "));
        http.perform(get(BASE + "/9001")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(9001));
        http.perform(patch(BASE + "/9001").contentType("application/json").content("{\"transactionReference\":\"  ref  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transactionReference").value("  ref  "));
        verify(patch).execute(new PatchPaymentCommand(9001L, "  ref  "));
    }
    @ParameterizedTest @ValueSource(strings = {"", "{}", "null", "[]",
            "{\"orderId\":501}", "{\"orderId\":null,\"method\":\"COD\"}",
            "{\"orderId\":501.5,\"method\":\"COD\"}", "{\"orderId\":\"501\",\"method\":\"COD\"}",
            "{\"orderId\":9223372036854775808,\"method\":\"COD\"}",
            "{\"orderId\":501,\"method\":0}", "{\"orderId\":501,\"method\":\"cod\"}",
            "{\"orderId\":501,\"method\":\"COD\",\"amount\":1}",
            "{\"orderId\":501,\"method\":\"COD\",\"status\":\"SUCCESS\"}",
            "{\"orderId\":501,\"orderId\":502,\"method\":\"COD\"}",
            "{\"orderId\":501,\"method\":\"COD\"} {}"})
    void create_whenInvalid_shouldRejectBeforeUseCase(String body) throws Exception {
        http.perform(post(BASE).header("Idempotency-Key", "key").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(create);
    }
    @ParameterizedTest @ValueSource(strings = {"", " key", "key ", "key/key", "é"})
    void key_whenInvalid_shouldRejectBeforeClaim(String key) throws Exception {
        http.perform(post(BASE).header("Idempotency-Key", key).contentType("application/json")
                .content("{\"orderId\":501,\"method\":\"COD\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(create);
    }
    @Test void key_whenMissingOrTooLong_shouldReject() throws Exception {
        http.perform(post(BASE).contentType("application/json").content("{\"orderId\":501,\"method\":\"COD\"}"))
                .andExpect(status().isBadRequest());
        http.perform(post(BASE).header("Idempotency-Key", "a".repeat(129)).contentType("application/json")
                .content("{\"orderId\":501,\"method\":\"COD\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(create);
    }
    @ParameterizedTest @ValueSource(strings = {"", "{}", "{\"transactionReference\":null}",
            "{\"transactionReference\":\"\"}", "{\"transactionReference\":\"  \\t\"}",
            "{\"transactionReference\":\" \"}", "{\"transactionReference\":12}",
            "{\"transactionReference\":\"ref\",\"amount\":1}",
            "{\"transactionReference\":\"ref\",\"referenceValid\":true}",
            "{\"transactionReference\":\"ref\",\"transactionReference\":\"other\"}"})
    void patch_whenInvalid_shouldRejectBeforeMutation(String body) throws Exception {
        http.perform(patch(BASE + "/9001").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(patch);
    }
    @ParameterizedTest @ValueSource(strings = {"SUCCESS", "FAILED"})
    void simulator_whenAuthorized_shouldCallResultUseCase(String value) throws Exception {
        when(apply.execute(any())).thenReturn(payment(PaymentStatus.valueOf(value), null));
        http.perform(post("/internal/dev/payments/9001/result").header("X-Simulator-Token", TOKEN)
                .contentType("application/json").content("{\"status\":\"" + value + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(value));
        verify(apply).execute(new ApplyPaymentResultCommand(9001L, PaymentStatus.valueOf(value)));
    }
    @Test void simulator_whenTokenMissingWrongOrRepeated_shouldRejectBeforeReadingInvalidBody() throws Exception {
        String url = "/internal/dev/payments/9001/result";
        http.perform(post(url).contentType("application/json").content("not json")).andExpect(status().isUnauthorized());
        http.perform(post(url).header("X-Simulator-Token", "wrong").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        http.perform(post(url).header("X-Simulator-Token", TOKEN, TOKEN).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(apply);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"status\":\"PENDING\"}", "{\"status\":null}",
            "{\"status\":0}", "{\"status\":\"SUCCESS\",\"amount\":1}"})
    void simulator_whenPayloadInvalid_shouldReject(String body) throws Exception {
        http.perform(post("/internal/dev/payments/9001/result").header("X-Simulator-Token", TOKEN)
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(apply);
    }
}
