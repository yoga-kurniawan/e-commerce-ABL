package id.ac.ub.abl.payment.usecase.port.out;

import java.util.Optional;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.usecase.command.ApplyPaymentResultCommand;
import id.ac.ub.abl.payment.usecase.command.PatchPaymentCommand;

/** Kebutuhan persistence aggregate; bukan repository JPA. */
public interface PaymentStore {
    Optional<Payment> findById(Long paymentId);

    /** Transaksi lokal: lock guard lalu Payment, baca ulang state/version, simpan perubahan. */
    Payment changeReference(PatchPaymentCommand command);

    /** Atomic state transition; hasil terminal identik tidak melakukan UPDATE/timestamp/version. */
    Payment applyResult(ApplyPaymentResultCommand command);
}
