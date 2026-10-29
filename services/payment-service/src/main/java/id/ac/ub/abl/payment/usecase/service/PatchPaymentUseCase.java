package id.ac.ub.abl.payment.usecase.service;

import java.util.Objects;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.usecase.command.PatchPaymentCommand;
import id.ac.ub.abl.payment.usecase.port.out.PaymentStore;
import org.springframework.stereotype.Service;

@Service
public class PatchPaymentUseCase {
    private final PaymentStore store;
    public PatchPaymentUseCase(PaymentStore store) { this.store = store; }
    public Payment execute(PatchPaymentCommand command) { return store.changeReference(Objects.requireNonNull(command, "command")); }
}
