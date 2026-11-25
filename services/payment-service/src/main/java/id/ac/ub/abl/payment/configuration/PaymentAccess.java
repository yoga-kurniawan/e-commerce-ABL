package id.ac.ub.abl.payment.configuration;

import id.ac.ub.abl.security.CustomerIdentity;
import id.ac.ub.abl.payment.usecase.service.GetPaymentUseCase;
import id.ac.ub.abl.payment.usecase.port.out.OrderLookup;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import org.springframework.stereotype.Component;

@Component("paymentAccess")
public class PaymentAccess {
    private final CustomerIdentity identity;
    private final GetPaymentUseCase get;
    private final OrderLookup orders;
    public PaymentAccess(CustomerIdentity identity, GetPaymentUseCase get, OrderLookup orders) {
        this.identity = identity; this.get = get; this.orders = orders;
    }
    public boolean owner(Long id) { return order(get.execute(id).getOrderId()); }
    public boolean order(Long id) {
        if (identity.isAdmin()) { return true; }
        identity.customerId(); // Require customer identity even for a correctly signed token.
        // Order verifies its owner against the relayed JWT. Never infer ownership from client input.
        orders.findById(id).orElseThrow(() -> new PaymentOperationException("ORDER_NOT_FOUND", "Order tidak ditemukan"));
        return true;
    }
}
