package id.ac.ub.abl.payment.adapter.inbound.rest.dto;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import id.ac.ub.abl.payment.domain.PaymentMethod;
import id.ac.ub.abl.payment.domain.PaymentStatus;
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "orderId", "method", "status", "amount", "transactionReference"})
public record PaymentOutput(Long id, Long orderId, PaymentMethod method, PaymentStatus status,
                            BigDecimal amount, String transactionReference) { }
