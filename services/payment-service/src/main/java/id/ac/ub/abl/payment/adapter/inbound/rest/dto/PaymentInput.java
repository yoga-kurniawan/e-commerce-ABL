package id.ac.ub.abl.payment.adapter.inbound.rest.dto;
import id.ac.ub.abl.payment.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
public record PaymentInput(@NotNull Long orderId, @NotNull PaymentMethod method) { }
