package id.ac.ub.abl.product.usecase.service;

import java.util.List;
import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.usecase.port.out.ProductStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListProductsUseCase {
    private final ProductStore store;

    public ListProductsUseCase(ProductStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public List<Product> execute() {
        return store.findAll();
    }
}
