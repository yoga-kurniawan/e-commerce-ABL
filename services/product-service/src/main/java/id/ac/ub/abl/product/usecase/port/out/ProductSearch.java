package id.ac.ub.abl.product.usecase.port.out;

import id.ac.ub.abl.product.usecase.command.ProductQuery;
import id.ac.ub.abl.product.usecase.result.ProductSearchResult;

public interface ProductSearch {
    ProductSearchResult search(ProductQuery query);
}
