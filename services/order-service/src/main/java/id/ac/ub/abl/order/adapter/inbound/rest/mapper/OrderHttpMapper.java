package id.ac.ub.abl.order.adapter.inbound.rest.mapper;

import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderInput;
import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderOutput;
import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderPatchInput;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.usecase.command.CreateOrderCommand;
import id.ac.ub.abl.order.usecase.command.PatchField;
import id.ac.ub.abl.order.usecase.command.PatchOrderCommand;
import org.springframework.stereotype.Component;

@Component
public class OrderHttpMapper {

    public CreateOrderCommand toCreateCommand(OrderInput input) {
        return new CreateOrderCommand(input.cartId(), input.customerId());
    }

    public PatchOrderCommand toPatchCommand(Long orderId, OrderPatchInput input) {
        return new PatchOrderCommand(orderId, input.hasShippingAddress()
                ? PatchField.supplied(input.getShippingAddress()) : PatchField.absent());
    }

    public OrderOutput toOutput(Order order) {
        return new OrderOutput(order.getId(), order.getCustomerId(), order.getTotal(),
                order.getStatus().name(), order.getShippingAddress());
    }
}
