package id.ac.ub.abl.order.adapter.inbound.rest;

import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderInput;
import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderOutput;
import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderPatchInput;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** Kontrak HTTP; annotation route didefinisikan pada interface ini. PUT/DELETE tidak disediakan. */
@RequestMapping(value = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public interface OrdersApi {

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<OrderOutput> createOrder(@Valid @RequestBody OrderInput input);

    @GetMapping("/{orderId}")
    ResponseEntity<OrderOutput> getOrder(@PathVariable("orderId") Long orderId);

    @PatchMapping(value = "/{orderId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<OrderOutput> updateOrder(@PathVariable("orderId") Long orderId,
                                            @Valid @RequestBody OrderPatchInput input);
}
