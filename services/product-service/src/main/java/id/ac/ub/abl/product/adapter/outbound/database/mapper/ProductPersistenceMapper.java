package id.ac.ub.abl.product.adapter.outbound.database.mapper;

import id.ac.ub.abl.product.adapter.outbound.database.entity.ProductJpaEntity;
import id.ac.ub.abl.product.domain.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductPersistenceMapper {
    public ProductJpaEntity toEntity(Product product) {
        return new ProductJpaEntity(product.getId(), product.getName(), product.getPrice(), product.getStock(), product.isActive());
    }

    public Product toDomain(ProductJpaEntity entity) {
        return Product.restore(entity.getId(), entity.getName(), entity.getPrice(), entity.getStock(), entity.isActive());
    }
}
