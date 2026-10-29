package id.ac.ub.abl.payment.usecase.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import id.ac.ub.abl.payment.domain.PaymentMethod;
import id.ac.ub.abl.payment.usecase.exception.InvalidPaymentCommandException;
import id.ac.ub.abl.payment.usecase.model.IdempotencyKey;

public record CreatePaymentCommand(Long orderId, PaymentMethod method, IdempotencyKey key) {
    public CreatePaymentCommand {
        if (orderId == null || method == null || key == null) {
            throw new InvalidPaymentCommandException("Order, metode, dan key wajib diisi");
        }
    }

    /** Tidak memakai JSON mentah atau key; fingerprint merepresentasikan payload. */
    public String canonicalPayload() {
        return "v1\norderId=" + orderId + "\nmethod=" + method.name();
    }

    public String fingerprint() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 tidak tersedia pada runtime", exception);
        }
    }

    /** Bandingkan nilai aktual juga, bukan bergantung hanya pada hash. */
    public boolean hasSamePayload(CreatePaymentCommand other) {
        return other != null && orderId.equals(other.orderId) && method == other.method;
    }
}
