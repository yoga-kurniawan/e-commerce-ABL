package id.ac.ub.abl.order.adapter.outbound.rest;

import java.util.Optional;

import id.ac.ub.abl.order.adapter.outbound.rest.dto.ProductResponse;
import id.ac.ub.abl.order.adapter.outbound.rest.mapper.ProductRestMapper;
import id.ac.ub.abl.order.domain.ProductSnapshot;
import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.order.usecase.exception.ProductServiceUnavailableException;
import id.ac.ub.abl.order.usecase.port.out.ProductLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ProductRestAdapter implements ProductLookup {
    private static final Logger LOG = LoggerFactory.getLogger(ProductRestAdapter.class);

    private final RestClient restClient;
    private final ProductRestMapper mapper;

    public ProductRestAdapter(@Qualifier("productRestClient") RestClient restClient, ProductRestMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
    }

    @Override
    public Optional<ProductSnapshot> findById(Long productId) {
        ProductResponse response;
        try {
            response = restClient.get()
                    .uri("/api/v1/products/{productId}", productId)
                    .retrieve()
                    .body(ProductResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            LOG.error("Gagal menghubungi Product Service untuk productId: {}", productId, ex);
            throw new ProductServiceUnavailableException("Gagal menghubungi Product Service", ex);
        }
        try {
            return Optional.ofNullable(response).map(mapper::toSnapshot);
        } catch (DomainRuleViolation ex) {
            throw new ProductServiceUnavailableException("Respons Product Service tidak valid", ex);
        }
    }
}
