package id.ac.ub.abl.cart.usecase.service;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.usecase.command.AddCartItemCommand;
import id.ac.ub.abl.cart.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import id.ac.ub.abl.cart.usecase.port.out.ProductLookup;
import org.springframework.stereotype.Service;

/** Lookup HTTP Product dilakukan di luar transaksi database; penyimpanan memakai transaksi lokal singkat. */
@Service
public class AddCartItemUseCase {
    private final CartStore cartStore;
    private final ProductLookup productLookup;

    public AddCartItemUseCase(CartStore cartStore, ProductLookup productLookup) {
        this.cartStore = cartStore;
        this.productLookup = productLookup;
    }

    public Cart execute(AddCartItemCommand command) {
        cartStore.findById(command.cartId())
                .orElseThrow(() -> new CartNotFoundException(command.cartId()));

        ProductSnapshot product = productLookup.findById(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        return cartStore.update(command.cartId(), cart -> cart.addItem(product, command.quantity()))
                .orElseThrow(() -> new CartNotFoundException(command.cartId()));
    }
}
