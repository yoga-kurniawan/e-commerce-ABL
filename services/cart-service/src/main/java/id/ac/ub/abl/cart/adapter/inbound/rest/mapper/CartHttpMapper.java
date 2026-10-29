package id.ac.ub.abl.cart.adapter.inbound.rest.mapper;

import java.util.List;

import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemOutput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemPatchInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemQuantityInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartOutput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.UpdatedCartItemOutput;
import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.CartItem;
import id.ac.ub.abl.cart.usecase.command.AddCartItemCommand;
import id.ac.ub.abl.cart.usecase.command.CreateCartCommand;
import id.ac.ub.abl.cart.usecase.command.PatchCartItemCommand;
import id.ac.ub.abl.cart.usecase.command.PatchField;
import id.ac.ub.abl.cart.usecase.command.ReplaceCartItemCommand;
import org.springframework.stereotype.Component;

@Component
public class CartHttpMapper {

    public CreateCartCommand toCreateCommand(CartInput input) {
        return new CreateCartCommand(input.customerId());
    }

    public AddCartItemCommand toAddCommand(Long cartId, CartItemInput input) {
        return new AddCartItemCommand(cartId, input.productId(), input.quantity());
    }

    public ReplaceCartItemCommand toReplaceCommand(Long cartId, Long productId, CartItemQuantityInput input) {
        return new ReplaceCartItemCommand(cartId, productId, input.quantity());
    }

    public PatchCartItemCommand toPatchCommand(Long cartId, Long productId, CartItemPatchInput input) {
        return new PatchCartItemCommand(cartId, productId,
                input.hasQuantity() ? PatchField.supplied(input.getQuantity()) : PatchField.absent());
    }

    public CartOutput toOutput(Cart cart) {
        List<CartItemOutput> items = cart.getItems().stream()
                .map(this::toItemOutput)
                .toList();
        return new CartOutput(cart.getId(), cart.getCustomerId(), items, cart.getTotal());
    }

    public CartItemOutput toItemOutput(CartItem item) {
        return new CartItemOutput(item.getProductId(), item.getQuantity(), item.getSubtotal());
    }

    public UpdatedCartItemOutput toUpdatedItemOutput(Long cartId, CartItem item) {
        return new UpdatedCartItemOutput(cartId, item.getProductId(), item.getQuantity(), item.getSubtotal());
    }
}
