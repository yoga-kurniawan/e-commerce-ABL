package id.ac.ub.abl.payment.adapter.inbound.rest;

import java.math.BigDecimal;
import java.util.List;
import id.ac.ub.abl.payment.adapter.exception.GlobalExceptionHandler;
import id.ac.ub.abl.payment.adapter.inbound.rest.mapper.PaymentHttpMapper;
import id.ac.ub.abl.payment.domain.*;
import id.ac.ub.abl.payment.domain.exception.PaymentNotModifiableException;
import id.ac.ub.abl.payment.usecase.command.*;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import id.ac.ub.abl.payment.usecase.model.*;
import id.ac.ub.abl.payment.usecase.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PaymentsApiController.class, PaymentsApiV2Controller.class},
        properties = "payment.simulator.enabled=false")
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, PaymentHttpMapper.class, PaymentJsonConfiguration.class})
class PaymentsApiV2HttpTest {
    private static final String BASE = "/api/v2/payments";
    private static final String INPUT = "{\"orderId\":501,\"method\":\"COD\"}";
    @Autowired MockMvc http;
    @MockitoBean CreatePaymentUseCase create;
    @MockitoBean GetPaymentUseCase get;
    @MockitoBean PatchPaymentUseCase patch;
    @MockitoBean ListPaymentsUseCase list;

    private Payment payment(long id, PaymentStatus status, String reference) {
        return Payment.restore(id, 501L, PaymentMethod.COD, new BigDecimal("350000.12500"), status, reference);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void create_whenNewOrReplayed_shouldReturnExactStoredResponseWithoutReadingCurrentState(boolean replay) throws Exception {
        var original = new StoredCreationResponse(201, "application/json",
                "{\n  \"id\":9001,\"orderId\":501,\"method\":\"COD\",\"status\":\"PENDING\",\"amount\":350000.12500\n}");
        when(create.execute(any())).thenReturn(replay ? new CreatePaymentResult.Replayed(original)
                : new CreatePaymentResult.Created(payment(9001, PaymentStatus.PENDING, null), original));

        http.perform(post(BASE).header("Idempotency-Key", "Key-1").contentType("application/json").content(INPUT))
                .andExpect(status().isCreated()).andExpect(content().contentType("application/json"))
                .andExpect(content().string(original.body())).andExpect(jsonPath("$.transactionReference").doesNotExist());

        verify(create).execute(new CreatePaymentCommand(501L, PaymentMethod.COD, new IdempotencyKey("Key-1")));
        verifyNoMoreInteractions(create);
        verifyNoInteractions(get, patch, list);
    }

    @Test void get_whenFound_shouldReusePublicDtoAndOmitNullReference() throws Exception {
        when(get.execute(9001L)).thenReturn(payment(9001, PaymentStatus.PENDING, null));
        http.perform(get(BASE + "/9001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9001)).andExpect(jsonPath("$.orderId").value(501))
                .andExpect(jsonPath("$.method").value("COD")).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.transactionReference").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amount\":350000.12500")));
        verify(get).execute(9001L);
        verifyNoInteractions(create, patch, list);
    }

    @Test void patch_whenValid_shouldKeepReferenceWhitespaceAndUseExistingCommand() throws Exception {
        when(patch.execute(any())).thenReturn(payment(9001, PaymentStatus.PENDING, "  ref  "));
        http.perform(patch(BASE + "/9001").contentType("application/json")
                .content("{\"transactionReference\":\"  ref  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transactionReference").value("  ref  "))
                .andExpect(jsonPath("$.status").value("PENDING"));
        verify(patch).execute(new PatchPaymentCommand(9001L, "  ref  "));
        verifyNoInteractions(create, get, list);
    }

    @Test void list_whenHistoryExists_shouldReturnArrayInUseCaseOrderWithOptionalReference() throws Exception {
        when(list.execute(501L)).thenReturn(List.of(payment(9001, PaymentStatus.FAILED, "failed-ref"),
                payment(9002, PaymentStatus.PENDING, null)));
        http.perform(get(BASE).param("orderId", "501")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(9001)).andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].transactionReference").value("failed-ref"))
                .andExpect(jsonPath("$[1].id").value(9002)).andExpect(jsonPath("$[1].status").value("PENDING"))
                .andExpect(jsonPath("$[1].transactionReference").doesNotExist());
        verify(list).execute(501L);
        verifyNoInteractions(create, get, patch);
    }

    @Test void list_whenEmpty_shouldReturn200WithEmptyArray() throws Exception {
        when(list.execute(501L)).thenReturn(List.of());
        http.perform(get(BASE).param("orderId", "501")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        verify(list).execute(501L);
    }

    @Test void list_whenOrderIdMissing_shouldReturnBadRequestWithoutCallingUseCase() throws Exception {
        http.perform(get(BASE)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(list);
    }

    @ParameterizedTest @ValueSource(strings = {"", "abc", "1.5", "null", "9223372036854775808"})
    void list_whenOrderIdInvalid_shouldReturnBadRequestWithoutCallingUseCase(String orderId) throws Exception {
        http.perform(get(BASE).param("orderId", orderId)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(list);
    }

    @ParameterizedTest @ValueSource(longs = {Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE})
    void list_whenOrderIdFitsLong_shouldNotIntroduceNewRangeRestrictions(long orderId) throws Exception {
        when(list.execute(orderId)).thenReturn(List.of());
        http.perform(get(BASE).param("orderId", Long.toString(orderId))).andExpect(status().isOk());
        verify(list).execute(orderId);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"orderId\":501.5,\"method\":\"COD\"}",
            "{\"orderId\":501,\"method\":\"cod\"}", "{\"orderId\":501,\"method\":\"COD\",\"amount\":1}"})
    void create_whenBodyInvalid_shouldReturnBadRequestBeforeUseCase(String body) throws Exception {
        http.perform(post(BASE).header("Idempotency-Key", "key").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(create);
    }

    @ParameterizedTest @ValueSource(strings = {"", " key", "key/key"})
    void create_whenKeyInvalid_shouldReturnBadRequestBeforeClaim(String key) throws Exception {
        http.perform(post(BASE).header("Idempotency-Key", key).contentType("application/json").content(INPUT))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(create);
    }

    @Test void create_whenKeyMissingOrTooLong_shouldReturnBadRequest() throws Exception {
        http.perform(post(BASE).contentType("application/json").content(INPUT))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        http.perform(post(BASE).header("Idempotency-Key", "a".repeat(129)).contentType("application/json").content(INPUT))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(create);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"transactionReference\":null}",
            "{\"transactionReference\":\"  \"}", "{\"transactionReference\":\"ref\",\"status\":\"SUCCESS\"}"})
    void patch_whenBodyInvalid_shouldReturnBadRequestWithoutMutation(String body) throws Exception {
        http.perform(patch(BASE + "/9001").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(patch);
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "9223372036854775808"})
    void detail_whenPathIdInvalid_shouldReturnBadRequest(String id) throws Exception {
        http.perform(get(BASE + "/" + id)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        http.perform(patch(BASE + "/" + id).contentType("application/json").content("{\"transactionReference\":\"ref\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(get, patch);
    }

    @ParameterizedTest @CsvSource({"ORDER_NOT_FOUND,404", "ORDER_NOT_PAYABLE,409", "PAYMENT_ALREADY_PENDING,409",
            "ORDER_ALREADY_PAID,409", "IDEMPOTENCY_KEY_REUSED,409", "REQUEST_IN_PROGRESS,409", "PAYMENT_OPERATION_BUSY,409",
            "DEPENDENCY_UNAVAILABLE,503", "ORDER_UPSTREAM_ERROR,502", "ORDER_TIMEOUT,504"})
    void create_whenApplicationFails_shouldPreserveErrorContract(String code, int expectedStatus) throws Exception {
        when(create.execute(any())).thenThrow(new PaymentOperationException(code, "private-error-detail"));
        http.perform(post(BASE).header("Idempotency-Key", "key").contentType("application/json").content(INPUT))
                .andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-error-detail"))));
    }

    @ParameterizedTest @CsvSource({"NOT_FOUND,404", "DEPENDENCY_UNAVAILABLE,503"})
    void get_whenApplicationFails_shouldPreserveErrorContract(String code, int expectedStatus) throws Exception {
        when(get.execute(9001L)).thenThrow(new PaymentOperationException(code, "private-error-detail"));
        http.perform(get(BASE + "/9001")).andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value(code));
    }

    @Test void patch_whenTerminalPaymentRejectsChanges_shouldReturnConflict() throws Exception {
        when(patch.execute(any())).thenThrow(new PaymentNotModifiableException());
        http.perform(patch(BASE + "/9001").contentType("application/json").content("{\"transactionReference\":\"ref\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_MODIFIABLE"));
    }

    @ParameterizedTest @ValueSource(ints = {500, 503})
    void list_whenApplicationFails_shouldReturnErrorObjectInsteadOfEmptySuccess(int expectedStatus) throws Exception {
        RuntimeException failure = expectedStatus == 503
                ? new PaymentOperationException("DEPENDENCY_UNAVAILABLE", "private-error-detail")
                : new IllegalStateException("private-error-detail");
        when(list.execute(501L)).thenThrow(failure);
        http.perform(get(BASE).param("orderId", "501")).andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedStatus == 503 ? "DEPENDENCY_UNAVAILABLE" : "INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-error-detail"))));
    }

    @Test void routes_whenBothVersionsExist_shouldKeepV1DetailAndRejectV1ListAndUnsupportedMethods() throws Exception {
        when(get.execute(9001L)).thenReturn(payment(9001, PaymentStatus.PENDING, null));
        http.perform(get("/api/v1/payments/9001")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(9001));
        http.perform(get(BASE + "/9001")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(9001));
        http.perform(get("/api/v1/payments").param("orderId", "501"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        http.perform(delete(BASE + "/9001")).andExpect(status().isMethodNotAllowed());
        verify(get, times(2)).execute(9001L);
        verifyNoInteractions(list, create, patch);
    }

    @Test void create_whenMediaTypeUnsupported_shouldReturn415BeforeUseCase() throws Exception {
        http.perform(post(BASE).header("Idempotency-Key", "key").contentType("text/plain").content(INPUT))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(create);
    }
}
