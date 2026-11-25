package id.ac.ub.abl.order.configuration;

import id.ac.ub.abl.security.CustomerIdentity;
import id.ac.ub.abl.order.usecase.service.GetOrderUseCase;
import org.springframework.stereotype.Component;

@Component("orderAccess")
public class OrderAccess {
    private final CustomerIdentity identity;
    private final GetOrderUseCase get;
    public OrderAccess(CustomerIdentity identity, GetOrderUseCase get) { this.identity = identity; this.get = get; }
    public boolean owner(Long id) { return identity.matches(get.execute(id).getCustomerId()); }
}
