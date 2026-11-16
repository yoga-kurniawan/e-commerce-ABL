package id.ac.ub.abl.order.adapter.outbound.rest.mapper;

import id.ac.ub.abl.order.adapter.outbound.rest.dto.PaymentResponse;
import id.ac.ub.abl.order.domain.PaymentSnapshot;
import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import org.springframework.stereotype.Component;

@Component
public class PaymentRestMapper {
    public PaymentSnapshot toSnapshot(PaymentResponse response) {
        if (response == null || response.status() == null) {
            throw new DomainRuleViolation("Data pembayaran tidak lengkap");
        }
        PaymentSnapshot.Status status;
        try {
            status = PaymentSnapshot.Status.valueOf(response.status());
        } catch (IllegalArgumentException ex) {
            throw new DomainRuleViolation("Status pembayaran tidak dikenal: " + response.status());
        }
        return new PaymentSnapshot(response.id(), response.orderId(), status);
    }
}
