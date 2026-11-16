package id.ac.ub.abl.product.usecase.result;

import java.util.List;
import id.ac.ub.abl.product.domain.Product;

public record ProductSearchResult(List<Product> products, long total) {
    public ProductSearchResult { products = List.copyOf(products); }
}
