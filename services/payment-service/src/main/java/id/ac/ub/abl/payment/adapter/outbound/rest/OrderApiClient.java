package id.ac.ub.abl.payment.adapter.outbound.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "/api/v1/orders", accept = "application/json")
public interface OrderApiClient {
    @GetExchange("/{orderId}")
    ResponseEntity<String> get(@PathVariable("orderId") Long orderId);
}
