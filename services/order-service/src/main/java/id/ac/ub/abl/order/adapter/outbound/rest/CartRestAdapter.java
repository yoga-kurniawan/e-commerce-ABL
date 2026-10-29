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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CartRestAdapter implements CartLookup {
    private static final Logger LOG = LoggerFactory.getLogger(CartRestAdapter.class);

    private final RestClient restClient;
    private final CartRestMapper mapper;

    public CartRestAdapter(@Qualifier("cartRestClient") RestClient restClient, CartRestMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
    }

    @Override
    public Optional<CartSnapshot> findById(Long cartId) {
        CartResponse response;
        try {
            response = restClient.get()
                    .uri("/api/v1/carts/{cartId}", cartId)
                    .retrieve()
                    .body(CartResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            LOG.error("Gagal menghubungi Cart Service untuk cartId: {}", cartId, ex);
            throw new CartServiceUnavailableException("Gagal menghubungi Cart Service", ex);
        }
        try {
            return Optional.ofNullable(response).map(mapper::toSnapshot);
        } catch (DomainRuleViolation ex) {
            throw new CartServiceUnavailableException("Respons Cart Service tidak valid", ex);
        }
    }
}
