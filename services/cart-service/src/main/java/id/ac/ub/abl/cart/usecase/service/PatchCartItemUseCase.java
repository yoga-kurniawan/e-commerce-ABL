package id.ac.ub.abl.cart.usecase.service;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.cart.usecase.command.PatchCartItemCommand;
import id.ac.ub.abl.cart.usecase.exception.CartItemNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import id.ac.ub.abl.cart.usecase.port.out.ProductLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatchCartItemUseCase {
    private final CartStore cartStore;
    private final ProductLookup productLookup;

    public PatchCartItemUseCase(CartStore cartStore, ProductLookup productLookup) {
        this.cartStore = cartStore;
        this.productLookup = productLookup;
    }

    @Transactional
    public Cart execute(PatchCartItemCommand command) {
        if (!command.hasChanges()) {
            throw new DomainRuleViolation("PATCH harus memuat minimal satu atribut");
        }
        if (command.quantity().value() == null) {
            throw new DomainRuleViolation("Quantity tidak boleh null");
        }
        Cart cart = cartStore.findById(command.cartId())
                .orElseThrow(() -> new CartNotFoundException(command.cartId()));

        if (cart.findItem(command.productId()).isEmpty()) {
            throw new CartItemNotFoundException(command.cartId(), command.productId());
        }

        ProductSnapshot product = productLookup.findById(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        Cart updated = cart.replaceItemQuantity(command.productId(), command.quantity().value(), product.stock());
        return cartStore.save(updated);
    }
}
