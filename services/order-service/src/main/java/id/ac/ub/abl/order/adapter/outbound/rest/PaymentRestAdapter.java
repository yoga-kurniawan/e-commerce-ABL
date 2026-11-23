package id.ac.ub.abl.order.adapter.outbound.rest;

import java.util.Arrays;
import java.util.List;

import id.ac.ub.abl.order.adapter.outbound.rest.dto.PaymentResponse;
import id.ac.ub.abl.order.adapter.outbound.rest.mapper.PaymentRestMapper;
import id.ac.ub.abl.order.domain.PaymentSnapshot;
import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.order.usecase.exception.PaymentServiceUnavailableException;
import id.ac.ub.abl.order.usecase.port.out.PaymentLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class PaymentRestAdapter implements PaymentLookup {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentRestAdapter.class);

    private final PaymentApiClient client;
    private final PaymentRestMapper mapper;

    public PaymentRestAdapter(PaymentApiClient client, PaymentRestMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public List<PaymentSnapshot> findByOrderId(Long orderId) {
        ResponseEntity<PaymentResponse[]> response;
        try {
            response = client.findByOrderId(orderId);
        } catch (RestClientException ex) {
            // Termasuk 4xx/5xx, timeout, dan JSON yang bukan array: status pembayaran tidak dapat dipastikan.
            LOG.error("Gagal menghubungi Payment Service untuk orderId: {}", orderId, ex);
            throw new PaymentServiceUnavailableException("Gagal menghubungi Payment Service", ex);
        }
        if (response.getStatusCode().value() != 200 || response.getBody() == null) {
            throw new PaymentServiceUnavailableException("Respons Payment Service kosong", null);
        }
        try {
            List<PaymentSnapshot> payments = Arrays.stream(response.getBody()).map(mapper::toSnapshot).toList();
            if (payments.stream().anyMatch(payment -> !payment.orderId().equals(orderId))) {
                throw new DomainRuleViolation("Payment Service mengembalikan pembayaran pesanan lain");
            }
            return payments;
        } catch (DomainRuleViolation ex) {
            throw new PaymentServiceUnavailableException("Respons Payment Service tidak valid", ex);
        }
    }
}
