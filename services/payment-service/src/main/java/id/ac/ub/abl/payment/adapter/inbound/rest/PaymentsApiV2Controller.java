package id.ac.ub.abl.payment.adapter.inbound.rest;

import java.util.List;
import id.ac.ub.abl.payment.adapter.inbound.rest.dto.PaymentInput;
import id.ac.ub.abl.payment.adapter.inbound.rest.dto.PaymentOutput;
import id.ac.ub.abl.payment.adapter.inbound.rest.dto.PaymentReferencePatch;
import id.ac.ub.abl.payment.adapter.inbound.rest.mapper.PaymentHttpMapper;
import id.ac.ub.abl.payment.usecase.service.CreatePaymentUseCase;
import id.ac.ub.abl.payment.usecase.service.GetPaymentUseCase;
import id.ac.ub.abl.payment.usecase.service.ListPaymentsUseCase;
import id.ac.ub.abl.payment.usecase.service.PatchPaymentUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentsApiV2Controller implements PaymentsApiV2 {
    private final CreatePaymentUseCase create;
    private final GetPaymentUseCase get;
    private final PatchPaymentUseCase patch;
    private final ListPaymentsUseCase list;
    private final PaymentHttpMapper mapper;

    public PaymentsApiV2Controller(CreatePaymentUseCase create, GetPaymentUseCase get,
            PatchPaymentUseCase patch, ListPaymentsUseCase list, PaymentHttpMapper mapper) {
        this.create = create; this.get = get; this.patch = patch; this.list = list; this.mapper = mapper;
    }

    @Override
    public ResponseEntity<String> createPayment(String key, PaymentInput input) {
        var response = create.execute(mapper.toCreateCommand(input, key)).response();
        return ResponseEntity.status(response.statusCode())
                .contentType(MediaType.parseMediaType(response.contentType())).body(response.body());
    }

    @Override
    public ResponseEntity<PaymentOutput> getPayment(Long id) {
        return ResponseEntity.ok(mapper.toOutput(get.execute(id)));
    }

    @Override
    public ResponseEntity<PaymentOutput> patchPayment(Long id, PaymentReferencePatch input) {
        return ResponseEntity.ok(mapper.toOutput(patch.execute(mapper.toPatchCommand(id, input))));
    }

    @Override
    public ResponseEntity<List<PaymentOutput>> listPayments(Long orderId) {
        return ResponseEntity.ok(list.execute(orderId).stream().map(mapper::toOutput).toList());
    }
}
