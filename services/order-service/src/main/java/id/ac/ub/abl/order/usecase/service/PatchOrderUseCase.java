package id.ac.ub.abl.order.usecase.service;

import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.order.usecase.command.PatchOrderCommand;
import id.ac.ub.abl.order.usecase.exception.OrderNotFoundException;
import id.ac.ub.abl.order.usecase.port.out.OrderStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PATCH hanya mengubah shippingAddress; total dan status tidak dapat diubah client. */
@Service
public class PatchOrderUseCase {
    private final OrderStore store;

    public PatchOrderUseCase(OrderStore store) {
        this.store = store;
    }

    @Transactional
    public Order execute(PatchOrderCommand command) {
        if (!command.hasChanges()) {
            throw new DomainRuleViolation("PATCH harus memuat minimal satu atribut pesanan");
        }
        Order order = store.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        return store.save(order.changeShippingAddress(command.shippingAddress().value()));
    }
}
