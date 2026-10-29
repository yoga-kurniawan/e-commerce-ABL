package id.ac.ub.abl.order.adapter.outbound.rest;

import java.math.BigDecimal;
import java.util.Optional;

import id.ac.ub.abl.order.adapter.outbound.rest.mapper.CartRestMapper;
import id.ac.ub.abl.order.domain.CartSnapshot;
import id.ac.ub.abl.order.usecase.exception.CartServiceUnavailableException;
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

class CartRestAdapterTest {
    private MockRestServiceServer mockServer;
    private CartRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://cart-service");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new CartRestAdapter(builder.build(), new CartRestMapper());
    }

    @Test
    void findById_whenCartExists_returnsSnapshotWithStoredSubtotals() {
        mockServer.expect(requestTo("http://cart-service/api/v1/carts/10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                            "id": 10,
                            "customerId": 101,
                            "items": [
                                {"productId": 1, "quantity": 2, "subtotal": 700000.250}
                            ],
                            "total": 700000.250
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<CartSnapshot> result = adapter.findById(10L);

        CartSnapshot cart = result.orElseThrow();
        assertEquals(10L, cart.id());
        assertEquals(101L, cart.customerId());
        assertEquals(1, cart.items().size());
        assertEquals(2, cart.items().get(0).quantity());
        assertEquals(0, new BigDecimal("350000.125").compareTo(cart.items().get(0).unitPrice()));
        mockServer.verify();
    }

    @Test
    void findById_whenCartIsEmpty_returnsEmptySnapshot() {
        mockServer.expect(requestTo("http://cart-service/api/v1/carts/10"))
                .andRespond(withSuccess("{\"id\":10,\"customerId\":101,\"items\":[],\"total\":0}",
                        MediaType.APPLICATION_JSON));

        assertTrue(adapter.findById(10L).orElseThrow().isEmpty());
    }

    @Test
    void findById_whenCartNotFound_returnsEmpty() {
        mockServer.expect(requestTo("http://cart-service/api/v1/carts/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(adapter.findById(999L).isEmpty());
        mockServer.verify();
    }

    @Test
    void findById_whenServerError_throwsCartServiceUnavailable() {
        mockServer.expect(requestTo("http://cart-service/api/v1/carts/10"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(CartServiceUnavailableException.class, () -> adapter.findById(10L));
    }

    @Test
    void findById_whenResponseInvalid_throwsCartServiceUnavailable() {
        mockServer.expect(requestTo("http://cart-service/api/v1/carts/10"))
                .andRespond(withSuccess("{\"id\":10,\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThrows(CartServiceUnavailableException.class, () -> adapter.findById(10L));
    }
}
