package id.ac.ub.abl.product.usecase.service;

import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.product.usecase.port.out.ProductStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetProductUseCase {
    private final ProductStore store;

    public GetProductUseCase(ProductStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public Product execute(Long productId) {
        return store.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
