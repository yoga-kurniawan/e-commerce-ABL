package id.ac.ub.abl.product.usecase.service;

import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.usecase.command.CreateProductCommand;
import id.ac.ub.abl.product.usecase.port.out.ProductStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateProductUseCase {
    private final ProductStore store;

    public CreateProductUseCase(ProductStore store) {
        this.store = store;
    }

    @Transactional
    public Product execute(CreateProductCommand command) {
        return store.save(Product.create(command.name(), command.price(), command.stock()));
    }
}
