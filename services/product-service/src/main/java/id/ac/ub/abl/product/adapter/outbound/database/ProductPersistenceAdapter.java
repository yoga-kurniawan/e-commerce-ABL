package id.ac.ub.abl.product.adapter.outbound.database;

import java.util.List;
import java.util.Optional;

import id.ac.ub.abl.product.adapter.outbound.database.mapper.ProductPersistenceMapper;
import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.usecase.port.out.ProductStore;
import id.ac.ub.abl.product.usecase.port.out.ProductSearch;
import id.ac.ub.abl.product.usecase.command.ProductQuery;
import id.ac.ub.abl.product.usecase.result.ProductSearchResult;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Menghubungkan port aplikasi dengan repository JPA lokal. */
@Repository
@Transactional(readOnly = true)
public class ProductPersistenceAdapter implements ProductStore, ProductSearch {
    private final ProductsRepository repository;
    private final ProductPersistenceMapper mapper;

    public ProductPersistenceAdapter(ProductsRepository repository, ProductPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Product save(Product product) {
        var entity = product.getId() == null
                ? mapper.toEntity(product)
                : repository.findById(product.getId())
                        .map(existing -> {
                            mapper.updateEntity(existing, product);
                            return existing;
                        })
                        .orElseGet(() -> mapper.toEntity(product));
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<Product> findById(Long productId) {
        return repository.findByIdAndActiveTrue(productId).map(mapper::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return repository.findAllByActiveTrueOrderByIdAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public ProductSearchResult search(ProductQuery query) {
        long total = repository.countMatchingActive(query.name());
        var products = repository.searchActive(query.name(), query.limit(), query.offset())
                .stream().map(mapper::toDomain).toList();
        return new ProductSearchResult(products, total);
    }

    @Override
    @Transactional
    public void delete(Product product) {
        save(product.deactivate());
    }
}
