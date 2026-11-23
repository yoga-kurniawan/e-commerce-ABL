package id.ac.ub.abl.order.adapter.outbound.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import id.ac.ub.abl.order.adapter.outbound.rest.dto.ProductResponse;
import id.ac.ub.abl.order.adapter.outbound.rest.mapper.ProductRestMapper;
import id.ac.ub.abl.order.domain.ProductSnapshot;
import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.order.usecase.exception.ProductServiceUnavailableException;
import id.ac.ub.abl.order.usecase.port.out.ProductLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Component
public class ProductRestAdapter implements ProductLookup {
    private static final Logger LOG = LoggerFactory.getLogger(ProductRestAdapter.class);

    private final ProductApiClient client;
    private final ProductRestMapper mapper;
    private final Executor productLookupExecutor;

    public ProductRestAdapter(
            ProductApiClient client,
            ProductRestMapper mapper,
            @Qualifier("productLookupExecutor") Executor productLookupExecutor) {
        this.client = client;
        this.mapper = mapper;
        this.productLookupExecutor = productLookupExecutor;
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
            throw new ProductServiceUnavailableException("Gagal menghubungi Product Service", ex);
        }
        if (response.getStatusCode().value() != 200 || response.getBody() == null) {
            throw new ProductServiceUnavailableException("Respons Product Service tidak sesuai kontrak", null);
        }
        try {
            return Optional.of(mapper.toSnapshot(response.getBody()));
        } catch (DomainRuleViolation ex) {
            throw new ProductServiceUnavailableException("Respons Product Service tidak valid", ex);
        }
    }

    @Override
    public Map<Long, Optional<ProductSnapshot>> findAllByIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<Long> uniqueIds = productIds.stream().distinct().toList();
        if (uniqueIds.size() == 1) {
            Long id = uniqueIds.get(0);
            return Map.of(id, findById(id));
        }

        Map<Long, CompletableFuture<Optional<ProductSnapshot>>> futures = new LinkedHashMap<>();
        for (Long id : uniqueIds) {
            futures.put(id, CompletableFuture.supplyAsync(() -> findById(id), productLookupExecutor));
        }

        Map<Long, Optional<ProductSnapshot>> results = new LinkedHashMap<>();
        for (Map.Entry<Long, CompletableFuture<Optional<ProductSnapshot>>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().join());
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException runtimeEx) {
                    throw runtimeEx;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new ProductServiceUnavailableException("Gagal menghubungi Product Service", cause);
            }
        }
        return results;
    }
}
