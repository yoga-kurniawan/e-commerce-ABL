package id.ac.ub.abl.order.usecase.service;

import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.usecase.exception.OrderNotFoundException;
import id.ac.ub.abl.order.usecase.port.out.OrderStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetOrderUseCase {
    private final OrderStore store;

    public GetOrderUseCase(OrderStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public Order execute(Long orderId) {
        return store.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
