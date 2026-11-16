package id.ac.ub.abl.order.usecase.service;

import java.util.List;

import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.usecase.port.out.OrderStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Riwayat pesanan satu customerId, terbaru dulu (DEC-022). customerId bukan bukti otorisasi (OPEN-010). */
@Service
public class ListOrdersUseCase {
    private final OrderStore store;

    public ListOrdersUseCase(OrderStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public List<Order> execute(Long customerId) {
        return store.findByCustomerId(customerId);
    }
}
