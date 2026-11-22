package id.ac.ub.abl.order.adapter.outbound.rest;

import java.util.Optional;

import id.ac.ub.abl.order.adapter.outbound.rest.dto.CartResponse;
import id.ac.ub.abl.order.adapter.outbound.rest.mapper.CartRestMapper;
import id.ac.ub.abl.order.domain.CartSnapshot;
import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.order.usecase.exception.CartServiceUnavailableException;
import id.ac.ub.abl.order.usecase.port.out.CartLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Component
public class CartRestAdapter implements CartLookup {
    private static final Logger LOG = LoggerFactory.getLogger(CartRestAdapter.class);

    private final CartApiClient client;
    private final CartRestMapper mapper;

    public CartRestAdapter(CartApiClient client, CartRestMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Optional<CartSnapshot> findById(Long cartId) {
        ResponseEntity<CartResponse> response;
        try {
            response = client.getById(cartId);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            LOG.error("Gagal menghubungi Cart Service untuk cartId: {}", cartId, ex);
            throw new CartServiceUnavailableException("Gagal menghubungi Cart Service", ex);
        }
        if (response.getStatusCode().value() != 200 || response.getBody() == null) {
            throw new CartServiceUnavailableException("Respons Cart Service tidak sesuai kontrak", null);
        }
        try {
            return Optional.of(mapper.toSnapshot(response.getBody()));
        } catch (DomainRuleViolation ex) {
            throw new CartServiceUnavailableException("Respons Cart Service tidak valid", ex);
        }
    }
}
