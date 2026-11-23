package id.ac.ub.abl.order.adapter.outbound.rest;

import id.ac.ub.abl.order.adapter.outbound.rest.dto.PaymentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Declarative Spring Boot 4 HTTP Interface Client untuk Payment Service v2. */
@HttpExchange(url = "/api/v2/payments", accept = "application/json")
public interface PaymentApiClient {

    @GetExchange
    ResponseEntity<PaymentResponse[]> findByOrderId(@RequestParam("orderId") Long orderId);
}
