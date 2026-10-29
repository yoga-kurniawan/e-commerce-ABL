package id.ac.ub.abl.payment.adapter.inbound.rest.simulator;
import id.ac.ub.abl.payment.adapter.inbound.rest.dto.PaymentOutput;
import id.ac.ub.abl.payment.adapter.inbound.rest.mapper.PaymentHttpMapper;
import id.ac.ub.abl.payment.usecase.command.ApplyPaymentResultCommand;
import id.ac.ub.abl.payment.usecase.service.ApplyPaymentResultUseCase;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
@RestController
@Profile("(local | test) & !prod")
@ConditionalOnProperty(name = "payment.simulator.enabled", havingValue = "true")
public class DevelopmentPaymentResultController {
    private final ApplyPaymentResultUseCase apply;
    private final PaymentHttpMapper mapper;
    public DevelopmentPaymentResultController(ApplyPaymentResultUseCase apply, PaymentHttpMapper mapper) { this.apply = apply; this.mapper = mapper; }
    @PostMapping(value = "/internal/dev/payments/{paymentId}/result", consumes = "application/json", produces = "application/json")
    public PaymentOutput result(@PathVariable("paymentId") Long id, @Valid @RequestBody SimulatedPaymentResult input) {
        return mapper.toOutput(apply.execute(new ApplyPaymentResultCommand(id, input.toStatus())));
    }
}
