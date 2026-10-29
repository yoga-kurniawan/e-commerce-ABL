package id.ac.ub.abl.order.adapter.outbound.rest;

import java.math.BigDecimal;

import id.ac.ub.abl.order.adapter.outbound.rest.mapper.ProductRestMapper;
import id.ac.ub.abl.order.domain.ProductSnapshot;
import id.ac.ub.abl.order.usecase.exception.ProductServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductRestAdapterTest {
    private MockRestServiceServer mockServer;
    private ProductRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://product-service");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new ProductRestAdapter(builder.build(), new ProductRestMapper());
    }

    @Test
    void findById_whenProductExists_returnsSnapshot() {
        mockServer.expect(requestTo("http://product-service/api/v1/products/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id": 1, "name": "Keyboard Mechanical", "price": 350000, "stock": 20}
                        """, MediaType.APPLICATION_JSON));

        ProductSnapshot product = adapter.findById(1L).orElseThrow();

        assertEquals("Keyboard Mechanical", product.name());
        assertEquals(new BigDecimal("350000"), product.price());
        assertEquals(20, product.stock());
        mockServer.verify();
    }

    @Test
    void findById_whenProductNotFoundOrInactive_returnsEmpty() {
        mockServer.expect(requestTo("http://product-service/api/v1/products/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(adapter.findById(999L).isEmpty());
        mockServer.verify();
    }

    @Test
    void findById_whenServerError_throwsProductServiceUnavailable() {
        mockServer.expect(requestTo("http://product-service/api/v1/products/1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(ProductServiceUnavailableException.class, () -> adapter.findById(1L));
    }
}
