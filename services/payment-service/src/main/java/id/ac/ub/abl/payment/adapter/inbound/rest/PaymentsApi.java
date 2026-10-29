package id.ac.ub.abl.payment.adapter.inbound.rest;
import id.ac.ub.abl.payment.adapter.inbound.rest.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RequestMapping(value = "/api/v1/payments", produces = "application/json")
public interface PaymentsApi {
    @PostMapping(consumes = "application/json")
    ResponseEntity<String> createPayment(@RequestHeader("Idempotency-Key")
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") String key, @Valid @RequestBody PaymentInput input);
    @GetMapping("/{paymentId}")
    ResponseEntity<PaymentOutput> getPayment(@PathVariable("paymentId") Long id);
    @PatchMapping(value = "/{paymentId}", consumes = "application/json")
    ResponseEntity<PaymentOutput> patchPayment(@PathVariable("paymentId") Long id, @Valid @RequestBody PaymentReferencePatch input);
}
