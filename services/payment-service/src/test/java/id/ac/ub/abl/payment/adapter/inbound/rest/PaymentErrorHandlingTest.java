package id.ac.ub.abl.payment.adapter.inbound.rest;

import id.ac.ub.abl.payment.adapter.exception.GlobalExceptionHandler;
import id.ac.ub.abl.payment.adapter.inbound.rest.mapper.PaymentHttpMapper;
import id.ac.ub.abl.payment.adapter.inbound.rest.simulator.*;
import id.ac.ub.abl.payment.domain.exception.*;
import id.ac.ub.abl.payment.usecase.exception.*;
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
import org.springframework.test.web.servlet.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PaymentsApiController.class, DevelopmentPaymentResultController.class,
        PaymentErrorHandlingTest.FrameworkErrorController.class}, properties = {"payment.simulator.enabled=true",
        "server.address=127.0.0.1", "payment.simulator.token=test-only-simulator-token-000000000"})
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, PaymentHttpMapper.class, PaymentJsonConfiguration.class, PaymentSimulatorConfiguration.class,
        PaymentErrorHandlingTest.FrameworkErrorController.class})
class PaymentErrorHandlingTest {
    static final String LEAK = "private-token-SQL-password-example";
    static final String BASE = "/api/v1/payments";
    @Autowired MockMvc http;
    @MockitoBean CreatePaymentUseCase create;
    @MockitoBean GetPaymentUseCase get;
    @MockitoBean PatchPaymentUseCase patch;
    @MockitoBean ApplyPaymentResultUseCase apply;

    @ParameterizedTest @CsvSource({"BAD_REQUEST,400", "NOT_FOUND,404", "ORDER_NOT_FOUND,404", "ORDER_NOT_PAYABLE,409",
            "PAYMENT_ALREADY_PENDING,409", "ORDER_ALREADY_PAID,409", "IDEMPOTENCY_KEY_REUSED,409", "REQUEST_IN_PROGRESS,409",
            "PAYMENT_OPERATION_BUSY,409", "PAYMENT_NOT_MODIFIABLE,409", "PAYMENT_RESULT_CONFLICT,409",
            "ORDER_UPSTREAM_ERROR,502", "DEPENDENCY_UNAVAILABLE,503", "ORDER_TIMEOUT,504"})
    void applicationErrors_whenRaised_shouldMapStatusCodeAndHideMessages(String code, int status) throws Exception {
        when(create.execute(any())).thenThrow(new PaymentOperationException(code, LEAK, new IllegalStateException(LEAK)));
        assertError(http.perform(post(BASE).header("Idempotency-Key", "key").contentType("application/json")
                .content("{\"orderId\":501,\"method\":\"COD\"}")), status, code);
    }
    @ParameterizedTest @ValueSource(strings = {"not-a-public-code", "INTERNAL_SERVER_ERROR"})
    void unknownCode_whenReturnedByPort_shouldFailClosedToInternalError(String code) throws Exception {
        when(get.execute(1L)).thenThrow(new PaymentOperationException(code, LEAK));
        assertError(http.perform(get(BASE + "/1")), 500, "INTERNAL_SERVER_ERROR");
    }
    @Test void nullCode_whenReturnedByPort_shouldProduceInternalError() throws Exception {
        when(get.execute(1L)).thenThrow(new PaymentOperationException(null, LEAK));
        assertError(http.perform(get(BASE + "/1")), 500, "INTERNAL_SERVER_ERROR");
    }
    @Test void domainErrors_whenRaised_shouldUseKnownPublicMessages() throws Exception {
        when(create.execute(any())).thenThrow(new OrderNotPayableException(501L));
        assertError(http.perform(post(BASE).header("Idempotency-Key", "key").contentType("application/json")
                .content("{\"orderId\":501,\"method\":\"COD\"}")), 409, "ORDER_NOT_PAYABLE");
        when(patch.execute(any())).thenThrow(new PaymentNotModifiableException());
        assertError(http.perform(patch(BASE + "/1").contentType("application/json").content("{\"transactionReference\":\"ref\"}")), 409, "PAYMENT_NOT_MODIFIABLE");
        when(apply.execute(any())).thenThrow(new PaymentResultConflictException());
        assertError(http.perform(post("/internal/dev/payments/1/result").header("X-Simulator-Token", PaymentsHttpBoundaryTest.TOKEN)
                .contentType("application/json").content("{\"status\":\"SUCCESS\"}")), 409, "PAYMENT_RESULT_CONFLICT");
    }
    @Test void invalidDomainOrCommand_whenRaised_shouldReturnBadRequestWithoutRawMessage() throws Exception {
        when(get.execute(1L)).thenThrow(new DomainRuleViolation(LEAK));
        assertError(http.perform(get(BASE + "/1")), 400, "BAD_REQUEST");
        when(get.execute(2L)).thenThrow(new InvalidPaymentCommandException(LEAK));
        assertError(http.perform(get(BASE + "/2")), 400, "BAD_REQUEST");
    }
    @Test void unexpectedOrRollbackWrapper_whenEscapingUseCase_shouldHideCauseAndStackTrace() throws Exception {
        when(get.execute(1L)).thenThrow(new IllegalStateException(LEAK));
        assertError(http.perform(get(BASE + "/1")), 500, "INTERNAL_SERVER_ERROR");
        when(get.execute(2L)).thenThrow(new PaymentCreationRolledBackException(new IllegalStateException(LEAK)));
        assertError(http.perform(get(BASE + "/2")), 500, "INTERNAL_SERVER_ERROR");
    }
    @ParameterizedTest @ValueSource(strings = {"", "{}", "null", "invalid-json", "{\"orderId\":501,\"method\":\"COD\",\"extra\":1}",
            "{\"orderId\":501,\"orderId\":502,\"method\":\"COD\"}", "{\"orderId\":1.5,\"method\":\"COD\"}"})
    void frameworkInputErrors_whenParsedOrValidated_shouldUseApiError(String body) throws Exception {
        assertError(http.perform(post(BASE).header("Idempotency-Key", "key").contentType("application/json").content(body)), 400, "BAD_REQUEST");
        verifyNoInteractions(create);
    }
    @ParameterizedTest @ValueSource(strings = {"", "{}", "null", "{\"transactionReference\":null}",
            "{\"transactionReference\":\"  \"}", "{\"transactionReference\":\"ref\",\"status\":\"SUCCESS\"}"})
    void patch_whenBodyInvalid_shouldUseApiErrorBeforeMutation(String body) throws Exception {
        assertError(http.perform(patch(BASE + "/1").contentType("application/json").content(body)), 400, "BAD_REQUEST");
        verifyNoInteractions(patch);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"status\":\"PENDING\"}", "{\"status\":null}",
            "{\"status\":0}", "{\"status\":\"SUCCESS\",\"amount\":1}"})
    void simulator_whenBodyInvalid_shouldUseApiErrorBeforeMutation(String body) throws Exception {
        assertError(http.perform(post("/internal/dev/payments/1/result").header("X-Simulator-Token", PaymentsHttpBoundaryTest.TOKEN)
                .contentType("application/json").content(body)), 400, "BAD_REQUEST");
        verifyNoInteractions(apply);
    }
    @ParameterizedTest @CsvSource({"NOT_FOUND,404", "PAYMENT_OPERATION_BUSY,409", "DEPENDENCY_UNAVAILABLE,503"})
    void readAndMutations_whenPortFails_shouldUseSameErrorContract(String code, int status) throws Exception {
        when(get.execute(1L)).thenThrow(new PaymentOperationException(code, LEAK));
        when(patch.execute(any())).thenThrow(new PaymentOperationException(code, LEAK));
        when(apply.execute(any())).thenThrow(new PaymentOperationException(code, LEAK));
        assertError(http.perform(get(BASE + "/1")), status, code);
        assertError(http.perform(patch(BASE + "/1").contentType("application/json")
                .content("{\"transactionReference\":\"ref\"}")), status, code);
        assertError(http.perform(post("/internal/dev/payments/1/result").header("X-Simulator-Token", PaymentsHttpBoundaryTest.TOKEN)
                .contentType("application/json").content("{\"status\":\"FAILED\"}")), status, code);
    }
    @Test void missingHeaderInvalidKeyAndPath_whenRejected_shouldUseApiError() throws Exception {
        String body = "{\"orderId\":501,\"method\":\"COD\"}";
        assertError(http.perform(post(BASE).contentType("application/json").content(body)), 400, "BAD_REQUEST");
        assertError(http.perform(post(BASE).header("Idempotency-Key", "invalid/key").contentType("application/json").content(body)), 400, "BAD_REQUEST");
        assertError(http.perform(get(BASE + "/not-a-long")), 400, "BAD_REQUEST");
        assertError(http.perform(get(BASE + "/9223372036854775808")), 400, "BAD_REQUEST");
    }
    @Test void frameworkProtocolErrors_whenRaised_shouldKeepStatusHeadersAndJsonShape() throws Exception {
        assertError(http.perform(delete(BASE + "/1")), 405, "METHOD_NOT_ALLOWED")
                .andExpect(header().string("Allow", org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("GET"), org.hamcrest.Matchers.containsString("PATCH"))));
        assertError(http.perform(post(BASE).contentType("text/plain").content(LEAK)), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertError(http.perform(get(BASE + "/1").accept("application/xml")), 406, "NOT_ACCEPTABLE");
        assertError(http.perform(get("/missing-payment-route")), 404, "NOT_FOUND");
    }
    @Test void frameworkErrorResponse_whenHeadersProvided_shouldKeepRetryAfterAndDiscardProblemDetail() throws Exception {
        assertError(http.perform(get("/test-framework-error")), 503, "SERVICE_UNAVAILABLE").andExpect(header().string("Retry-After", "30"));
    }
    @ParameterizedTest @CsvSource({"401,UNAUTHORIZED", "403,FORBIDDEN", "429,TOO_MANY_REQUESTS", "499,HTTP_ERROR", "500,INTERNAL_SERVER_ERROR"})
    void frameworkStatus_whenErrorResponseRaised_shouldPreserveStatusAndReplaceProblemJson(int status, String code) throws Exception {
        assertError(http.perform(get("/test-framework-status/" + status)), status, code)
                .andExpect(header().string("Retry-After", "30"));
    }
    @Test void frameworkUnauthorized_whenChallengeProvided_shouldKeepAuthenticateHeader() throws Exception {
        assertError(http.perform(get("/test-framework-status/401")), 401, "UNAUTHORIZED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }
    @Test void frameworkResponseStatus_whenReasonContainsSecret_shouldPreserveStatusAndHideReason() throws Exception {
        assertError(http.perform(get("/test-response-status")), 429, "TOO_MANY_REQUESTS");
    }
    @Test void frameworkSerialization_whenFailureBeforeCommit_shouldUseSafeInternalError() throws Exception {
        assertError(http.perform(get("/test-write-failure")), 500, "INTERNAL_SERVER_ERROR");
    }
    @Test void unauthorizedSimulator_whenRejected_shouldHaveSameErrorShapeAndHideToken() throws Exception {
        assertError(http.perform(post("/internal/dev/payments/1/result").header("X-Simulator-Token", LEAK)
                .contentType("application/json").content("{}")), 401, "UNAUTHORIZED");
        verifyNoInteractions(apply);
    }
    ResultActions assertError(ResultActions action, int status, String code) throws Exception {
        return action.andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(2)))
                .andDo(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(LEAK, "stackTrace", "exception", "java.lang", "trace", "type", "detail"));
    }
    @RestController static class FrameworkErrorController {
        @GetMapping("/test-framework-error") Object fail() {
            var failure = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, LEAK), null);
            failure.getHeaders().set(HttpHeaders.RETRY_AFTER, "30");
            throw failure;
        }
        @GetMapping("/test-framework-status/{status}") Object status(@PathVariable("status") int status) {
            var failure = new ErrorResponseException(HttpStatusCode.valueOf(status),
                    ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), LEAK), null);
            failure.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            failure.getHeaders().set(HttpHeaders.RETRY_AFTER, "30");
            if (status == 401) { failure.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer"); }
            throw failure;
        }
        @GetMapping("/test-response-status") Object responseStatus() {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, LEAK);
        }
        @GetMapping("/test-write-failure") Object writeFailure() {
            throw new HttpMessageNotWritableException(LEAK);
        }
    }
}
