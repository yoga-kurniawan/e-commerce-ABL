package id.ac.ub.abl.payment.adapter.inbound.rest.simulator;
import id.ac.ub.abl.payment.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;
public record SimulatedPaymentResult(@NotNull Result status) {
    public enum Result { SUCCESS, FAILED }
    public PaymentStatus toStatus() { return PaymentStatus.valueOf(status.name()); }
}
