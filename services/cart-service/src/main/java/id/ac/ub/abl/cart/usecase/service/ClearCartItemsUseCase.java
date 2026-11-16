package id.ac.ub.abl.cart.usecase.service;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClearCartItemsUseCase {
    private final CartStore cartStore;

    public ClearCartItemsUseCase(CartStore cartStore) {
        this.cartStore = cartStore;
    }

    @Transactional
    public void execute(Long cartId) {
        Cart cart = cartStore.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));

        cartStore.save(cart.clearItems());
    }
}
