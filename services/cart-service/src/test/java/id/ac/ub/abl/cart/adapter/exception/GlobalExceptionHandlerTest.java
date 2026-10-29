package id.ac.ub.abl.cart.adapter.exception;

import id.ac.ub.abl.cart.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.cart.domain.exception.InsufficientStockException;
import id.ac.ub.abl.cart.usecase.exception.CartItemNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.ProductServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private final MockMvc http = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void domainRuleViolationReturns400WithContractError() throws Exception {
        http.perform(get("/test/domain"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"code\":\"BAD_REQUEST\",\"message\":\"Quantity item minimal 1\"}"));
    }

    @Test
    void cartNotFoundReturns404() throws Exception {
        http.perform(get("/test/cart-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Keranjang tidak ditemukan"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void cartItemNotFoundReturns404() throws Exception {
        http.perform(get("/test/item-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Item tidak ditemukan dalam keranjang"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void productNotFoundReturns404() throws Exception {
        http.perform(get("/test/product-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Produk tidak ditemukan"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void insufficientStockReturns409Conflict() throws Exception {
        http.perform(get("/test/stock-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Jumlah melebihi stok yang tersedia"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void dependencyFailureReturns503() throws Exception {
        http.perform(get("/test/dependency-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Layanan produk tidak dapat dihubungi"))
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
                case "domain" -> throw new DomainRuleViolation("Quantity item minimal 1");
                case "cart-missing" -> throw new CartNotFoundException(10L);
                case "item-missing" -> throw new CartItemNotFoundException(10L, 5L);
                case "product-missing" -> throw new ProductNotFoundException(5L);
                case "stock-conflict" -> throw new InsufficientStockException("Jumlah melebihi stok yang tersedia");
                case "dependency-failure" -> throw new ProductServiceUnavailableException("Timeout");
                default -> throw new IllegalStateException("Internal database detail that must not reach the client");
            }
        }
    }
}
