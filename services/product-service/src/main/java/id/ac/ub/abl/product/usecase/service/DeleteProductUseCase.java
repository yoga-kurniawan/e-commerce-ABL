package id.ac.ub.abl.product.usecase.service;

import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.product.usecase.port.out.ProductStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteProductUseCase {
    private final ProductStore store;

    public DeleteProductUseCase(ProductStore store) {
        this.store = store;
    }

    @Transactional
    public void execute(Long productId) {
        Product product = store.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        store.delete(product.deactivate());
    }
}
