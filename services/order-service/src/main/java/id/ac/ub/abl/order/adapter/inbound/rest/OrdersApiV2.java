package id.ac.ub.abl.order.adapter.inbound.rest;

import java.util.List;

import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderInput;
import id.ac.ub.abl.order.adapter.inbound.rest.dto.v2.OrderOutputV2;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping(value = "/api/v2/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public interface OrdersApiV2 {

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<OrderOutputV2> createOrder(@Valid @RequestBody OrderInput input);

    @GetMapping("/{orderId}")
    ResponseEntity<OrderOutputV2> getOrder(@PathVariable("orderId") Long orderId);

    @PostMapping("/{orderId}/cancel")
    ResponseEntity<OrderOutputV2> cancelOrder(@PathVariable("orderId") Long orderId);

    @GetMapping
    ResponseEntity<List<OrderOutputV2>> listOrders(@RequestParam("customerId") Long customerId);
}
