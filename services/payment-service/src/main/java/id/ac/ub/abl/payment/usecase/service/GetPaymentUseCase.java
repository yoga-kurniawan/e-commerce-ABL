package id.ac.ub.abl.payment.usecase.service;

import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.usecase.exception.InvalidPaymentCommandException;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import id.ac.ub.abl.payment.usecase.port.out.PaymentStore;
import org.springframework.stereotype.Service;

@Service
public class GetPaymentUseCase {
    private final PaymentStore store;
    public GetPaymentUseCase(PaymentStore store) { this.store = store; }
    public Payment execute(Long paymentId) {
        if (paymentId == null) { throw new InvalidPaymentCommandException("ID Payment wajib diisi"); }
        return store.findById(paymentId).orElseThrow(() -> new PaymentOperationException("NOT_FOUND", "Payment tidak ditemukan"));
    }
}
