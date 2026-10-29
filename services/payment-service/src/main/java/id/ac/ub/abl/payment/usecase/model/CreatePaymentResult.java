package id.ac.ub.abl.payment.usecase.model;

import java.util.Objects;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.domain.PaymentStatus;

public sealed interface CreatePaymentResult {
    StoredCreationResponse response();

    record Created(Payment payment, StoredCreationResponse response) implements CreatePaymentResult {
        public Created {
            Objects.requireNonNull(payment, "payment");
            Objects.requireNonNull(response, "response");
            if (payment.getId() == null || payment.getStatus() != PaymentStatus.PENDING) {
                throw new IllegalArgumentException("Hasil create harus Payment tersimpan yang berstatus PENDING");
            }
        }
    }

    /** Tidak membawa domain terkini agar boundary tidak membentuk ulang respons replay. */
    record Replayed(StoredCreationResponse response) implements CreatePaymentResult {
        public Replayed { Objects.requireNonNull(response, "response"); }
    }
}
