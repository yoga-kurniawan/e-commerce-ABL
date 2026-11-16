package id.ac.ub.abl.payment.adapter.inbound.rest;

import java.util.List;
import id.ac.ub.abl.payment.adapter.inbound.rest.dto.PaymentInput;
import id.ac.ub.abl.payment.adapter.inbound.rest.dto.PaymentOutput;
import id.ac.ub.abl.payment.adapter.inbound.rest.dto.PaymentReferencePatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping(value = "/api/v2/payments", produces = "application/json")
public interface PaymentsApiV2 {
    @PostMapping(consumes = "application/json")
    ResponseEntity<String> createPayment(@RequestHeader("Idempotency-Key")
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") String key, @Valid @RequestBody PaymentInput input);

    @GetMapping("/{paymentId}")
    ResponseEntity<PaymentOutput> getPayment(@PathVariable("paymentId") Long id);

    @PatchMapping(value = "/{paymentId}", consumes = "application/json")
    ResponseEntity<PaymentOutput> patchPayment(@PathVariable("paymentId") Long id, @Valid @RequestBody PaymentReferencePatch input);

    @GetMapping
    ResponseEntity<List<PaymentOutput>> listPayments(@RequestParam("orderId") Long orderId);
}
