package id.ac.ub.abl.payment.usecase.command;

import id.ac.ub.abl.payment.domain.PaymentStatus;
import id.ac.ub.abl.payment.usecase.exception.InvalidPaymentCommandException;

/** Input use case hasil terpercaya/simulator, bukan PATCH bisnis publik. */
public record ApplyPaymentResultCommand(Long paymentId, PaymentStatus result) {
    public ApplyPaymentResultCommand {
        if (paymentId == null || result == null || result == PaymentStatus.PENDING) {
            throw new InvalidPaymentCommandException("ID Payment dan hasil SUCCESS/FAILED wajib diisi");
        }
    }
}
