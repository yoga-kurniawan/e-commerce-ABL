package id.ac.ub.abl.cart.adapter.outbound.rest;

import java.util.Optional;

import id.ac.ub.abl.cart.adapter.outbound.rest.dto.ProductResponse;
import id.ac.ub.abl.cart.adapter.outbound.rest.mapper.ProductRestMapper;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.cart.usecase.exception.ProductServiceUnavailableException;
import id.ac.ub.abl.cart.usecase.port.out.ProductLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Component
public class ProductRestAdapter implements ProductLookup {
    private static final Logger LOG = LoggerFactory.getLogger(ProductRestAdapter.class);

    private final ProductApiClient client;
    private final ProductRestMapper mapper;

    public ProductRestAdapter(ProductApiClient client, ProductRestMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Optional<ProductSnapshot> findById(Long productId) {
        ResponseEntity<ProductResponse> response;
        try {
            response = client.getById(productId);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            LOG.error("Gagal menghubungi Product Service untuk productId: {}", productId, ex);
            throw new ProductServiceUnavailableException("Gagal menghubungi Product Service: " + ex.getMessage(), ex);
        }
        if (response.getStatusCode().value() != 200 || response.getBody() == null) {
            throw new ProductServiceUnavailableException("Respons Product Service tidak sesuai kontrak");
        }
        try {
            return Optional.of(mapper.toSnapshot(response.getBody()));
        } catch (DomainRuleViolation ex) {
            throw new ProductServiceUnavailableException("Data Product Service tidak valid", ex);
        }
    }
}
