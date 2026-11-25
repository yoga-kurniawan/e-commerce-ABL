package id.ac.ub.abl.cart.configuration;

import id.ac.ub.abl.security.CustomerIdentity;
import id.ac.ub.abl.cart.usecase.service.GetCartUseCase;
import org.springframework.stereotype.Component;

@Component("cartAccess")
public class CartAccess {
    private final CustomerIdentity identity;
    private final GetCartUseCase get;
    public CartAccess(CustomerIdentity identity, GetCartUseCase get) { this.identity = identity; this.get = get; }
    public boolean owner(Long id) { return identity.matches(get.execute(id).getCustomerId()); }
}
