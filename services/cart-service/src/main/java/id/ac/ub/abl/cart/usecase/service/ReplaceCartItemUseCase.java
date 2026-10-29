package id.ac.ub.abl.cart.usecase.service;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.usecase.command.ReplaceCartItemCommand;
import id.ac.ub.abl.cart.usecase.exception.CartItemNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import id.ac.ub.abl.cart.usecase.port.out.ProductLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplaceCartItemUseCase {
    private final CartStore cartStore;
    private final ProductLookup productLookup;

    public ReplaceCartItemUseCase(CartStore cartStore, ProductLookup productLookup) {
        this.cartStore = cartStore;
        this.productLookup = productLookup;
    }

    @Transactional
    public Cart execute(ReplaceCartItemCommand command) {
        Cart cart = cartStore.findById(command.cartId())
                .orElseThrow(() -> new CartNotFoundException(command.cartId()));

        if (cart.findItem(command.productId()).isEmpty()) {
            throw new CartItemNotFoundException(command.cartId(), command.productId());
        }

        ProductSnapshot product = productLookup.findById(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        Cart updated = cart.replaceItemQuantity(command.productId(), command.quantity(), product.stock());
        return cartStore.save(updated);
    }
}
