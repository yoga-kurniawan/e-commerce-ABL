package id.ac.ub.abl.payment.adapter.inbound.rest.mapper;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.adapter.inbound.rest.dto.*;
import id.ac.ub.abl.payment.usecase.command.*;
import id.ac.ub.abl.payment.usecase.model.IdempotencyKey;
import org.springframework.stereotype.Component;
@Component
public class PaymentHttpMapper {
    public CreatePaymentCommand toCreateCommand(PaymentInput input, String key) {
        return new CreatePaymentCommand(input.orderId(), input.method(), new IdempotencyKey(key));
    }
    public PatchPaymentCommand toPatchCommand(Long id, PaymentReferencePatch input) {
        return new PatchPaymentCommand(id, input.transactionReference());
    }
    public PaymentOutput toOutput(Payment payment) {
        return new PaymentOutput(payment.getId(), payment.getOrderId(), payment.getMethod(), payment.getStatus(),
                payment.getAmount(), payment.getTransactionReference());
    }
}
