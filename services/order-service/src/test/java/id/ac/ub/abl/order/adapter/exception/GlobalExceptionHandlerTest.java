package id.ac.ub.abl.order.adapter.exception;

import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.order.domain.exception.OrderNotModifiableException;
import id.ac.ub.abl.order.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.order.usecase.exception.CartOwnershipMismatchException;
import id.ac.ub.abl.order.usecase.exception.CartServiceUnavailableException;
import id.ac.ub.abl.order.usecase.exception.EmptyCartException;
import id.ac.ub.abl.order.usecase.exception.InsufficientStockException;
import id.ac.ub.abl.order.usecase.exception.OrderNotFoundException;
import id.ac.ub.abl.order.usecase.exception.ProductServiceUnavailableException;
import id.ac.ub.abl.order.usecase.exception.ProductUnavailableException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private final MockMvc http = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @ParameterizedTest
    @CsvSource({
            "order-missing, 404, NOT_FOUND",
            "cart-missing, 404, NOT_FOUND",
            "cart-empty, 409, CONFLICT",
            "cart-mismatch, 409, CONFLICT",
            "product-unavailable, 409, CONFLICT",
            "stock, 409, CONFLICT",
            "not-modifiable, 409, CONFLICT",
            "domain, 400, BAD_REQUEST",
            "cart-down, 503, SERVICE_UNAVAILABLE",
            "product-down, 503, SERVICE_UNAVAILABLE",
            "unexpected, 500, INTERNAL_SERVER_ERROR"
    })
    void mapsExceptionsToContractErrors(String path, int status, String code) throws Exception {
        http.perform(get("/test/{path}", path))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void dependencyAndInternalErrorsHideInternalDetails() throws Exception {
        http.perform(get("/test/cart-down"))
                .andExpect(jsonPath("$.message").value("Layanan keranjang tidak dapat dihubungi"));
        http.perform(get("/test/unexpected"))
                .andExpect(jsonPath("$.message").value("Terjadi kesalahan pada server"));
    }

    @Test
    void conflictKeepsBusinessMessage() throws Exception {
        http.perform(get("/test/not-modifiable"))
                .andExpect(jsonPath("$.message").value("Alamat pengiriman tidak dapat diubah pada pesanan berstatus CONFIRMED"));
    }

    @Test
    void frameworkErrorsUseContractShape() throws Exception {
        http.perform(post("/test/order-missing"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @RestController
    static class FailingController {
        @GetMapping("/test/{path}")
        String fail(@PathVariable("path") String path) {
            throw switch (path) {
                case "order-missing" -> new OrderNotFoundException(1L);
                case "cart-missing" -> new CartNotFoundException(1L);
                case "cart-empty" -> new EmptyCartException(1L);
                case "cart-mismatch" -> new CartOwnershipMismatchException(1L, 2L);
                case "product-unavailable" -> new ProductUnavailableException(1L);
                case "stock" -> new InsufficientStockException(1L);
                case "not-modifiable" -> new OrderNotModifiableException(
                        "Alamat pengiriman tidak dapat diubah pada pesanan berstatus CONFIRMED");
                case "domain" -> new DomainRuleViolation("Alamat pengiriman tidak boleh kosong");
                case "cart-down" -> new CartServiceUnavailableException("connection refused at 10.0.0.1", null);
                case "product-down" -> new ProductServiceUnavailableException("timeout", null);
                default -> new IllegalStateException("secret internal detail");
            };
        }
    }
}
