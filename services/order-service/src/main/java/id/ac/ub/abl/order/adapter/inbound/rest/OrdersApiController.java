package id.ac.ub.abl.order.adapter.inbound.rest;

import java.net.URI;

import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderInput;
import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderOutput;
import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderPatchInput;
import id.ac.ub.abl.order.adapter.inbound.rest.mapper.OrderHttpMapper;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.usecase.service.CreateOrderUseCase;
import id.ac.ub.abl.order.usecase.service.GetOrderUseCase;
import id.ac.ub.abl.order.usecase.service.PatchOrderUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class OrdersApiController implements OrdersApi {
    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;
    private final PatchOrderUseCase patchOrder;
    private final OrderHttpMapper mapper;

    public OrdersApiController(CreateOrderUseCase createOrder,
                               GetOrderUseCase getOrder,
                               PatchOrderUseCase patchOrder,
                               OrderHttpMapper mapper) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
        this.patchOrder = patchOrder;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<OrderOutput> createOrder(OrderInput input) {
        Order order = createOrder.execute(mapper.toCreateCommand(input));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderId}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toOutput(order));
    }

    @Override
    public ResponseEntity<OrderOutput> getOrder(Long orderId) {
        return ResponseEntity.ok(mapper.toOutput(getOrder.execute(orderId)));
    }

    @Override
    public ResponseEntity<OrderOutput> updateOrder(Long orderId, OrderPatchInput input) {
        Order order = patchOrder.execute(mapper.toPatchCommand(orderId, input));
        return ResponseEntity.ok(mapper.toOutput(order));
    }
}
