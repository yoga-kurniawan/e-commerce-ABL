package id.ac.ub.abl.cart.usecase.service;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.usecase.command.CreateCartCommand;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCartUseCase {
    private final CartStore store;

    public CreateCartUseCase(CartStore store) {
        this.store = store;
    }

    @Transactional
    public Cart execute(CreateCartCommand command) {
        return store.save(Cart.create(command.customerId()));
    }
}
