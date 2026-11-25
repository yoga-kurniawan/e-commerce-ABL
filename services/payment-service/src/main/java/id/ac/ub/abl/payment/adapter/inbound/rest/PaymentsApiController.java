package id.ac.ub.abl.payment.adapter.inbound.rest;
import id.ac.ub.abl.payment.adapter.inbound.rest.dto.*;
import id.ac.ub.abl.payment.adapter.inbound.rest.mapper.PaymentHttpMapper;
import id.ac.ub.abl.payment.usecase.service.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class PaymentsApiController implements PaymentsApi {
    private final CreatePaymentUseCase create;
    private final GetPaymentUseCase get;
    private final PatchPaymentUseCase patch;
    private final PaymentHttpMapper mapper;
    public PaymentsApiController(CreatePaymentUseCase create, GetPaymentUseCase get, PatchPaymentUseCase patch, PaymentHttpMapper mapper) {
        this.create = create; this.get = get; this.patch = patch; this.mapper = mapper;
    }
    @PreAuthorize("@paymentAccess.order(#input.orderId())")
    public ResponseEntity<String> createPayment(String key, PaymentInput input) {
        var response = create.execute(mapper.toCreateCommand(input, key)).response();
        return ResponseEntity.status(response.statusCode()).contentType(MediaType.parseMediaType(response.contentType())).body(response.body());
    }
    @PreAuthorize("@paymentAccess.owner(#id)")
    public ResponseEntity<PaymentOutput> getPayment(Long id) { return ResponseEntity.ok(mapper.toOutput(get.execute(id))); }
    @PreAuthorize("@paymentAccess.owner(#id)")
    public ResponseEntity<PaymentOutput> patchPayment(Long id, PaymentReferencePatch input) {
        return ResponseEntity.ok(mapper.toOutput(patch.execute(mapper.toPatchCommand(id, input))));
    }
}
