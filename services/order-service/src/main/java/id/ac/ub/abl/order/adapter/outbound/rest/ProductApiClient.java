package id.ac.ub.abl.order.adapter.outbound.rest;

import id.ac.ub.abl.order.adapter.outbound.rest.dto.ProductResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Declarative Spring Boot 4 HTTP Interface Client untuk Product Service. */
@HttpExchange(url = "/api/v1/products", accept = "application/json")
public interface ProductApiClient {

    @GetExchange("/{productId}")
    ResponseEntity<ProductResponse> getById(@PathVariable("productId") Long productId);
}
