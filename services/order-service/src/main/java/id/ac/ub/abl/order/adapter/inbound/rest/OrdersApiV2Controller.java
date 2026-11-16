package id.ac.ub.abl.order.adapter.inbound.rest;

import java.net.URI;
import java.util.List;

import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderInput;
import id.ac.ub.abl.order.adapter.inbound.rest.dto.v2.OrderOutputV2;
import id.ac.ub.abl.order.adapter.inbound.rest.mapper.OrderHttpMapper;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.usecase.service.CancelOrderUseCase;
import id.ac.ub.abl.order.usecase.service.CreateOrderUseCase;
import id.ac.ub.abl.order.usecase.service.GetOrderUseCase;
import id.ac.ub.abl.order.usecase.service.ListOrdersUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class OrdersApiV2Controller implements OrdersApiV2 {
    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;
    private final ListOrdersUseCase listOrders;
    private final CancelOrderUseCase cancelOrder;
    private final OrderHttpMapper mapper;

    public OrdersApiV2Controller(CreateOrderUseCase createOrder,
                                 GetOrderUseCase getOrder,
                                 ListOrdersUseCase listOrders,
                                 CancelOrderUseCase cancelOrder,
                                 OrderHttpMapper mapper) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
        this.listOrders = listOrders;
        this.cancelOrder = cancelOrder;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<OrderOutputV2> createOrder(OrderInput input) {
        Order order = createOrder.execute(mapper.toCreateCommand(input));
        URI location = UriComponentsBuilder.fromPath("/api/v2/orders/{orderId}")
                .buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toOutputV2(order));
    }

    @Override
    public ResponseEntity<OrderOutputV2> getOrder(Long orderId) {
        return ResponseEntity.ok(mapper.toOutputV2(getOrder.execute(orderId)));
    }

    @Override
    public ResponseEntity<OrderOutputV2> cancelOrder(Long orderId) {
        return ResponseEntity.ok(mapper.toOutputV2(cancelOrder.execute(orderId)));
    }

    @Override
    public ResponseEntity<List<OrderOutputV2>> listOrders(Long customerId) {
        return ResponseEntity.ok(listOrders.execute(customerId).stream().map(mapper::toOutputV2).toList());
    }
}
