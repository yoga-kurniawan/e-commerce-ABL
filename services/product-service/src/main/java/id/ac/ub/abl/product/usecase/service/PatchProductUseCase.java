package id.ac.ub.abl.product.usecase.service;

import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.product.usecase.command.PatchProductCommand;
import id.ac.ub.abl.product.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.product.usecase.port.out.ProductStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatchProductUseCase {
    private final ProductStore store;

    public PatchProductUseCase(ProductStore store) {
        this.store = store;
    }

    @Transactional
    public Product execute(PatchProductCommand command) {
        if (!command.hasChanges()) {
            throw new DomainRuleViolation("PATCH harus memuat minimal satu atribut produk");
        }
        Product product = store.findById(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));
        // Field yang dikirim sebagai null tetap diteruskan agar ditolak oleh domain.
        Product updated = product.replace(
                command.name().supplied() ? command.name().value() : product.getName(),
                command.price().supplied() ? command.price().value() : product.getPrice(),
                command.stock().supplied() ? command.stock().value() : Integer.valueOf(product.getStock()));
        return store.save(updated);
    }
}
