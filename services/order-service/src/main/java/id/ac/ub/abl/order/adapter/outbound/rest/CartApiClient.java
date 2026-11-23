package id.ac.ub.abl.order.adapter.outbound.rest;

import id.ac.ub.abl.order.adapter.outbound.rest.dto.CartResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Declarative Spring Boot 4 HTTP Interface Client untuk Cart Service v2. */
@HttpExchange(url = "/api/v2/carts", accept = "application/json")
public interface CartApiClient {

    @GetExchange("/{cartId}")
    ResponseEntity<CartResponse> getById(@PathVariable("cartId") Long cartId);
}
