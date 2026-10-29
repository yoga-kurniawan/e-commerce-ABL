package id.ac.ub.abl.payment.domain;

import java.math.BigDecimal;

import id.ac.ub.abl.payment.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.payment.domain.exception.OrderNotPayableException;

/** Snapshot milik Payment; bukan DTO HTTP atau entity dari Order Service. */
public record OrderSnapshot(Long id, BigDecimal total, Status status) {
    public enum Status { PENDING, CONFIRMED, CANCELLED }

    public OrderSnapshot {
        if (id == null || status == null) {
            throw new DomainRuleViolation("ID dan status Order wajib diisi");
        }
        if (total == null || total.signum() < 0) {
            throw new DomainRuleViolation("Total Order wajib diisi dan tidak boleh negatif");
        }
    }

    public void requirePayable() {
        if (status != Status.PENDING) {
            throw new OrderNotPayableException(id);
        }
    }
}
