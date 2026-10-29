package id.ac.ub.abl.payment.usecase.model;

import java.time.Instant;
import java.util.Objects;
import id.ac.ub.abl.payment.usecase.command.CreatePaymentCommand;

/** Lease berasal dari database. Token harus diverifikasi lagi saat commit/release. */
public record IdempotencyClaim(CreatePaymentCommand request, String ownerToken, Instant leaseUntil) {
    public IdempotencyClaim {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("Owner token wajib tersedia");
        }
    }
}
