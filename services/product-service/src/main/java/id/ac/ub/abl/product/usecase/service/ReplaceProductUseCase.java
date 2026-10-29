package id.ac.ub.abl.product.usecase.service;

import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.usecase.command.ReplaceProductCommand;
import id.ac.ub.abl.product.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.product.usecase.port.out.ProductStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplaceProductUseCase {
    private final ProductStore store;

    public ReplaceProductUseCase(ProductStore store) {
        this.store = store;
    }

    @Transactional
    public Product execute(ReplaceProductCommand command) {
        Product product = store.findById(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));
        return store.save(product.replace(command.name(), command.price(), command.stock()));
    }
}
