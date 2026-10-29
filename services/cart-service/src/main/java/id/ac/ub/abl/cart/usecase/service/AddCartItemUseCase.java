package id.ac.ub.abl.cart.usecase.service;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.usecase.command.AddCartItemCommand;
import id.ac.ub.abl.cart.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import id.ac.ub.abl.cart.usecase.port.out.ProductLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddCartItemUseCase {
    private final CartStore cartStore;
    private final ProductLookup productLookup;

    public AddCartItemUseCase(CartStore cartStore, ProductLookup productLookup) {
        this.cartStore = cartStore;
        this.productLookup = productLookup;
    }

    @Transactional
    public Cart execute(AddCartItemCommand command) {
        Cart cart = cartStore.findById(command.cartId())
                .orElseThrow(() -> new CartNotFoundException(command.cartId()));

        ProductSnapshot product = productLookup.findById(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        Cart updated = cart.addItem(product, command.quantity());
        return cartStore.save(updated);
    }
}
