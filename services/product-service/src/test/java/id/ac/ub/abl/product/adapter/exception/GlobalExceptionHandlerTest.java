package id.ac.ub.abl.product.adapter.exception;

import id.ac.ub.abl.product.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.product.usecase.exception.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private final MockMvc http = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void domainRuleViolationReturns400WithContractError() throws Exception {
        http.perform(get("/test/domain"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"code\":\"BAD_REQUEST\",\"message\":\"Stok produk tidak boleh negatif\"}"));
    }

    @Test
    void missingProductReturns404WithoutInternalExceptionDetails() throws Exception {
        http.perform(get("/test/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Produk tidak ditemukan"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void unexpectedFailureReturnsGeneric500WithoutLeakingExceptionMessage() throws Exception {
        http.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Terjadi kesalahan pada server\"}"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void unsupportedMethodPreserves405AndAllowHeader() throws Exception {
        http.perform(post("/test/domain"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @RestController
    static class FailingController {
        @GetMapping("/test/{kind}")
        public void fail(@PathVariable("kind") String kind) {
            switch (kind) {
                case "domain" -> throw new DomainRuleViolation("Stok produk tidak boleh negatif");
                case "missing" -> throw new ProductNotFoundException(123L);
                default -> throw new IllegalStateException("Internal database detail that must not reach the client");
            }
        }
    }
}
