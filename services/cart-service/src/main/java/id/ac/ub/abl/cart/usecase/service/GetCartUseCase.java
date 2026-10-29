package id.ac.ub.abl.cart.usecase.service;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCartUseCase {
    private final CartStore store;

    public GetCartUseCase(CartStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public Cart execute(Long cartId) {
        return store.findById(cartId).orElseThrow(() -> new CartNotFoundException(cartId));
    }
}
