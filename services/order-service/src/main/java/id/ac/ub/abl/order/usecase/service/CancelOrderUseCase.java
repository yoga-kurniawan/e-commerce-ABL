package id.ac.ub.abl.order.usecase.service;

import java.util.List;

import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.domain.OrderStatus;
import id.ac.ub.abl.order.domain.PaymentSnapshot;
import id.ac.ub.abl.order.usecase.exception.OrderCancellationConflictException;
import id.ac.ub.abl.order.usecase.exception.OrderNotFoundException;
import id.ac.ub.abl.order.usecase.port.out.OrderStore;
import id.ac.ub.abl.order.usecase.port.out.PaymentLookup;
import org.springframework.stereotype.Service;

/**
 * Membatalkan pesanan PENDING (DEC-023). Lookup Payment dilakukan di luar transaksi database;
 * penyimpanan memakai transaksi lokal OrderStore. Race dengan create Payment bersamaan belum dimitigasi.
 */
@Service
public class CancelOrderUseCase {
    private final OrderStore store;
    private final PaymentLookup paymentLookup;

    public CancelOrderUseCase(OrderStore store, PaymentLookup paymentLookup) {
        this.store = store;
        this.paymentLookup = paymentLookup;
    }

    public Order execute(Long orderId) {
        Order order = load(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }

        order.cancel();

        List<PaymentSnapshot> payments = paymentLookup.findByOrderId(orderId);
        if (hasStatus(payments, PaymentSnapshot.Status.PENDING)) {
            throw new OrderCancellationConflictException("Pesanan masih memiliki pembayaran PENDING");
        }
        if (hasStatus(payments, PaymentSnapshot.Status.SUCCESS)) {
            throw new OrderCancellationConflictException("Pesanan sudah memiliki pembayaran SUCCESS");
        }

        Order latest = load(orderId);
        if (latest.getStatus() == OrderStatus.CANCELLED) {
            return latest;
        }
        return store.save(latest.cancel());
    }

    private Order load(Long orderId) {
        return store.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private static boolean hasStatus(List<PaymentSnapshot> payments, PaymentSnapshot.Status status) {
        return payments.stream().anyMatch(payment -> payment.status() == status);
    }
}
