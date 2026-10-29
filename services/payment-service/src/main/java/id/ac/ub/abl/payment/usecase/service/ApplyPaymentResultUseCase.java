package id.ac.ub.abl.payment.usecase.service;

import java.util.Objects;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.usecase.command.ApplyPaymentResultCommand;
import id.ac.ub.abl.payment.usecase.port.out.PaymentStore;
import org.springframework.stereotype.Service;

@Service
public class ApplyPaymentResultUseCase {
    private final PaymentStore store;
    public ApplyPaymentResultUseCase(PaymentStore store) { this.store = store; }
    public Payment execute(ApplyPaymentResultCommand command) { return store.applyResult(Objects.requireNonNull(command, "command")); }
}
