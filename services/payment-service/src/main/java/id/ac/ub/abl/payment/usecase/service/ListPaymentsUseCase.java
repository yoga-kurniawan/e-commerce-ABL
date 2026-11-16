package id.ac.ub.abl.payment.usecase.service;

import java.util.List;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.usecase.exception.InvalidPaymentCommandException;
import id.ac.ub.abl.payment.usecase.port.out.PaymentStore;
import org.springframework.stereotype.Service;

@Service
public class ListPaymentsUseCase {
    private final PaymentStore store;
    public ListPaymentsUseCase(PaymentStore store) { this.store = store; }
    public List<Payment> execute(Long orderId) {
        if (orderId == null) { throw new InvalidPaymentCommandException("ID Order wajib diisi"); }
        return store.findByOrderId(orderId);
    }
}
