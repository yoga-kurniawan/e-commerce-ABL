package id.ac.ub.abl.cart.adapter.inbound.rest;

import java.net.URI;

import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemPatchInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemQuantityInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.v2.CartOutputV2;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.v2.UpdatedCartItemOutputV2;
import id.ac.ub.abl.cart.adapter.inbound.rest.mapper.CartHttpMapper;
import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.CartItem;
import id.ac.ub.abl.cart.usecase.service.AddCartItemUseCase;
import id.ac.ub.abl.cart.usecase.service.ClearCartItemsUseCase;
import id.ac.ub.abl.cart.usecase.service.CreateCartUseCase;
import id.ac.ub.abl.cart.usecase.service.DeleteCartItemUseCase;
import id.ac.ub.abl.cart.usecase.service.GetCartUseCase;
import id.ac.ub.abl.cart.usecase.service.PatchCartItemUseCase;
import id.ac.ub.abl.cart.usecase.service.ReplaceCartItemUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class CartsApiV2Controller implements CartsApiV2 {
    private final CreateCartUseCase createCart;
    private final GetCartUseCase getCart;
    private final AddCartItemUseCase addCartItem;
    private final ReplaceCartItemUseCase replaceCartItem;
    private final PatchCartItemUseCase patchCartItem;
    private final DeleteCartItemUseCase deleteCartItem;
    private final CartHttpMapper mapper;
    private final ClearCartItemsUseCase clearCartItems;

    public CartsApiV2Controller(CreateCartUseCase createCart,
                              GetCartUseCase getCart,
                              AddCartItemUseCase addCartItem,
                              ReplaceCartItemUseCase replaceCartItem,
                              PatchCartItemUseCase patchCartItem,
                              DeleteCartItemUseCase deleteCartItem,
                              CartHttpMapper mapper,
                              ClearCartItemsUseCase clearCartItems) {
        this.createCart = createCart;
        this.getCart = getCart;
        this.addCartItem = addCartItem;
        this.replaceCartItem = replaceCartItem;
        this.patchCartItem = patchCartItem;
        this.deleteCartItem = deleteCartItem;
        this.mapper = mapper;
        this.clearCartItems = clearCartItems;
    }

    @Override
    public ResponseEntity<CartOutputV2> createCart(CartInput input) {
        Cart cart = createCart.execute(mapper.toCreateCommand(input));
        URI location = UriComponentsBuilder.fromPath("/api/v2/carts/{cartId}")
                .buildAndExpand(cart.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toOutputV2(cart));
    }

    @Override
    public ResponseEntity<CartOutputV2> getCart(Long cartId) {
        Cart cart = getCart.execute(cartId);
        return ResponseEntity.ok(mapper.toOutputV2(cart));
    }

    @Override
    public ResponseEntity<CartOutputV2> addItem(Long cartId, CartItemInput input) {
        Cart cart = addCartItem.execute(mapper.toAddCommand(cartId, input));
        URI location = UriComponentsBuilder.fromPath("/api/v2/carts/{cartId}/items/{productId}")
                .buildAndExpand(cartId, input.productId()).toUri();
        return ResponseEntity.created(location).body(mapper.toOutputV2(cart));
    }

    @Override
    public ResponseEntity<UpdatedCartItemOutputV2> replaceItem(Long cartId, Long productId, CartItemQuantityInput input) {
        Cart cart = replaceCartItem.execute(mapper.toReplaceCommand(cartId, productId, input));
        CartItem item = cart.findItem(productId).orElseThrow();
        return ResponseEntity.ok(mapper.toUpdatedItemOutputV2(cartId, item));
    }

    @Override
    public ResponseEntity<UpdatedCartItemOutputV2> updateItem(Long cartId, Long productId, CartItemPatchInput input) {
        Cart cart = patchCartItem.execute(mapper.toPatchCommand(cartId, productId, input));
        CartItem item = cart.findItem(productId).orElseThrow();
        return ResponseEntity.ok(mapper.toUpdatedItemOutputV2(cartId, item));
    }

    @Override
    public ResponseEntity<Void> deleteItem(Long cartId, Long productId) {
        deleteCartItem.execute(cartId, productId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> clearItems(Long cartId) {
        clearCartItems.execute(cartId);
        return ResponseEntity.noContent().build();
    }
}
