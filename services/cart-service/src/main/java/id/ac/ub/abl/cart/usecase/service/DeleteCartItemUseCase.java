package id.ac.ub.abl.cart.usecase.service;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.usecase.exception.CartItemNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteCartItemUseCase {
    private final CartStore cartStore;

    public DeleteCartItemUseCase(CartStore cartStore) {
        this.cartStore = cartStore;
    }

    @Transactional
    public void execute(Long cartId, Long productId) {
        Cart cart = cartStore.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException(cartId));

        if (cart.findItem(productId).isEmpty()) {
            throw new CartItemNotFoundException(cartId, productId);
        }

        Cart updated = cart.removeItem(productId);
        cartStore.save(updated);
    }
}
