package id.ac.ub.abl.payment.adapter.inbound.rest;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.adapter.inbound.rest.mapper.PaymentHttpMapper;
import id.ac.ub.abl.payment.usecase.model.StoredCreationResponse;
import id.ac.ub.abl.payment.usecase.port.out.CreationResponseEncoder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component
public class PaymentCreationResponseEncoder implements CreationResponseEncoder {
    private final JsonMapper json;
    private final PaymentHttpMapper mapper;
    public PaymentCreationResponseEncoder(JsonMapper json, PaymentHttpMapper mapper) { this.json = json; this.mapper = mapper; }
    public StoredCreationResponse encode(Payment payment) {
        return new StoredCreationResponse(201, "application/json", json.writeValueAsString(mapper.toOutput(payment)));
    }
}
