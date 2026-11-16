package id.ac.ub.abl.product.usecase.service;

import id.ac.ub.abl.product.usecase.command.ProductQuery;
import id.ac.ub.abl.product.usecase.port.out.ProductSearch;
import id.ac.ub.abl.product.usecase.result.ProductSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class SearchProductsUseCase {
    private final ProductSearch search;

    public SearchProductsUseCase(ProductSearch search) { this.search = search; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductSearchResult execute(ProductQuery query) { return search.search(query); }
}
