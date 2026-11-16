package id.ac.ub.abl.product.adapter.inbound.rest;

import java.net.URI;
import java.util.List;
import id.ac.ub.abl.product.adapter.inbound.rest.dto.v2.*;
import id.ac.ub.abl.product.adapter.inbound.rest.mapper.ProductHttpMapperV2;
import id.ac.ub.abl.product.usecase.command.ProductQuery;
import id.ac.ub.abl.product.usecase.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductsApiV2Controller implements ProductsApiV2 {
    private final CreateProductUseCase create;
    private final SearchProductsUseCase search;
    private final GetProductUseCase get;
    private final ReplaceProductUseCase replace;
    private final PatchProductUseCase patch;
    private final DeleteProductUseCase delete;
    private final ProductHttpMapperV2 mapper;

    public ProductsApiV2Controller(CreateProductUseCase create, SearchProductsUseCase search,
            GetProductUseCase get, ReplaceProductUseCase replace, PatchProductUseCase patch,
            DeleteProductUseCase delete, ProductHttpMapperV2 mapper) {
        this.create = create; this.search = search; this.get = get; this.replace = replace;
        this.patch = patch; this.delete = delete; this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<ProductOutputV2>> getProducts(String name, Integer page, Integer size) {
        var result = search.execute(new ProductQuery(name, page, size));
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(result.products().stream().map(mapper::toOutput).toList());
    }

    @Override
    public ResponseEntity<ProductOutputV2> getProductById(Long id) {
        return ResponseEntity.ok(mapper.toOutput(get.execute(id)));
    }

    @Override
    public ResponseEntity<ProductOutputV2> createProduct(ProductInputV2 input) {
        var output = mapper.toOutput(create.execute(mapper.toCreateCommand(input)));
        return ResponseEntity.created(URI.create("/api/v2/products/" + output.id())).body(output);
    }

    @Override
    public ResponseEntity<ProductOutputV2> replaceProduct(Long id, ProductInputV2 input) {
        return ResponseEntity.ok(mapper.toOutput(replace.execute(mapper.toReplaceCommand(id, input))));
    }

    @Override
    public ResponseEntity<ProductOutputV2> updateProduct(Long id, ProductPatchInputV2 input) {
        return ResponseEntity.ok(mapper.toOutput(patch.execute(mapper.toPatchCommand(id, input))));
    }

    @Override
    public ResponseEntity<Void> deleteProduct(Long id) {
        delete.execute(id);
        return ResponseEntity.noContent().build();
    }
}
