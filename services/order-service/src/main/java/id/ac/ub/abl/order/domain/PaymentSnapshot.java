package id.ac.ub.abl.order.domain;

import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;

/** Data Payment yang dibutuhkan Order untuk memutuskan pembatalan (DEC-023); dimiliki Order Service. */
public record PaymentSnapshot(Long id, Long orderId, Status status) {
    public enum Status { PENDING, SUCCESS, FAILED }

    public PaymentSnapshot {
        if (id == null) {
            throw new DomainRuleViolation("ID pembayaran wajib diisi");
        }
        if (orderId == null) {
            throw new DomainRuleViolation("ID pesanan pembayaran wajib diisi");
        }
        if (status == null) {
            throw new DomainRuleViolation("Status pembayaran wajib diisi");
        }
    }
}
