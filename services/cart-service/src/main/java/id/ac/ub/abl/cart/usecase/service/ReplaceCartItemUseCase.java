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

/** Lookup HTTP Product dilakukan di luar transaksi database; penyimpanan memakai transaksi lokal singkat. */
@Service
public class ReplaceCartItemUseCase {
    private final CartStore cartStore;
    private final ProductLookup productLookup;

    public ReplaceCartItemUseCase(CartStore cartStore, ProductLookup productLookup) {
        this.cartStore = cartStore;
        this.productLookup = productLookup;
    }

    public Cart execute(ReplaceCartItemCommand command) {
        Cart cart = cartStore.findById(command.cartId())
                .orElseThrow(() -> new CartNotFoundException(command.cartId()));

        if (cart.findItem(command.productId()).isEmpty()) {
            throw new CartItemNotFoundException(command.cartId(), command.productId());
        }

        ProductSnapshot product = productLookup.findById(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        return cartStore.update(command.cartId(), latest -> {
            if (latest.findItem(command.productId()).isEmpty()) {
                throw new CartItemNotFoundException(command.cartId(), command.productId());
            }
            return latest.replaceItemQuantity(command.productId(), command.quantity(), product.stock());
        }).orElseThrow(() -> new CartNotFoundException(command.cartId()));
    }
}
