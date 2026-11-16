package id.ac.ub.abl.order.adapter.outbound.rest;

import java.util.List;

import id.ac.ub.abl.order.adapter.outbound.rest.mapper.PaymentRestMapper;
import id.ac.ub.abl.order.domain.PaymentSnapshot;
import id.ac.ub.abl.order.usecase.exception.PaymentServiceUnavailableException;
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

/** Stub kontrak GET /api/v2/payments?orderId= (DEC-022) yang belum diimplementasikan Payment Service. */
class PaymentRestAdapterTest {
    private static final String URL = "http://payment-service/api/v2/payments?orderId=501";

    private MockRestServiceServer mockServer;
    private PaymentRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payment-service");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new PaymentRestAdapter(builder.build(), new PaymentRestMapper());
    }

    @Test
    void findByOrderId_mapsPaymentHistoryAndIgnoresOtherFields() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"id": 9001, "orderId": 501, "method": "TRANSFER", "status": "FAILED", "amount": 700000},
                          {"id": 9002, "orderId": 501, "method": "E_WALLET", "status": "PENDING", "amount": 700000,
                           "transactionReference": "TRX-DEMO-001"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<PaymentSnapshot> payments = adapter.findByOrderId(501L);

        assertEquals(List.of(
                new PaymentSnapshot(9001L, 501L, PaymentSnapshot.Status.FAILED),
                new PaymentSnapshot(9002L, 501L, PaymentSnapshot.Status.PENDING)), payments);
        mockServer.verify();
    }

    @Test
    void findByOrderId_withoutPayments_returnsEmptyList() {
        mockServer.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(adapter.findByOrderId(501L).isEmpty());
    }

    @Test
    void findByOrderId_whenServerErrorOrNotFound_throwsUnavailable() {
        mockServer.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThrows(PaymentServiceUnavailableException.class, () -> adapter.findByOrderId(501L));

        mockServer.reset();
        mockServer.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(PaymentServiceUnavailableException.class, () -> adapter.findByOrderId(501L));
    }

    @Test
    void findByOrderId_withInvalidResponse_throwsUnavailable() {
        List<String> invalidBodies = List.of(
                "{\"id\":9001,\"orderId\":501,\"status\":\"PENDING\"}",
                "[{\"id\":9001,\"orderId\":501,\"status\":\"REFUNDED\"}]",
                "[{\"id\":9001,\"orderId\":777,\"status\":\"FAILED\"}]",
                "[{\"id\":9001,\"orderId\":501}]",
                "[{\"orderId\":501,\"status\":\"FAILED\"}]");
        for (String body : invalidBodies) {
            mockServer.reset();
            mockServer.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThrows(PaymentServiceUnavailableException.class, () -> adapter.findByOrderId(501L), body);
        }
    }
}
