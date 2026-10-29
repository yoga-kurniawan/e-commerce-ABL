package id.ac.ub.abl.cart.adapter.outbound.rest;

import java.math.BigDecimal;
import java.util.Optional;

import id.ac.ub.abl.cart.adapter.outbound.rest.mapper.ProductRestMapper;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.usecase.exception.ProductServiceUnavailableException;
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
        RestClient restClient = builder.build();
        adapter = new ProductRestAdapter(restClient, new ProductRestMapper());
    }

    @Test
    void findById_whenProductExists_returnsSnapshot() {
        mockServer.expect(requestTo("http://product-service/api/v1/products/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                            "id": 1,
                            "name": "Keyboard Mechanical",
                            "price": 350000,
                            "stock": 20
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<ProductSnapshot> result = adapter.findById(1L);

        assertTrue(result.isPresent());
        ProductSnapshot snapshot = result.get();
        assertEquals(1L, snapshot.id());
        assertEquals("Keyboard Mechanical", snapshot.name());
        assertEquals(new BigDecimal("350000"), snapshot.price());
        assertEquals(20, snapshot.stock());
        mockServer.verify();
    }

    @Test
    void findById_whenProductNotFound_returnsEmpty() {
        mockServer.expect(requestTo("http://product-service/api/v1/products/999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<ProductSnapshot> result = adapter.findById(999L);

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void findById_whenServerError_throwsProductServiceUnavailableException() {
        mockServer.expect(requestTo("http://product-service/api/v1/products/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ProductServiceUnavailableException.class, () -> adapter.findById(1L));
        mockServer.verify();
    }
}
