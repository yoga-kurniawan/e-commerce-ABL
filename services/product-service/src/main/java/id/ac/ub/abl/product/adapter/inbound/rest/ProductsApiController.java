package id.ac.ub.abl.product.adapter.inbound.rest;

import java.util.List;

import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductInput;
import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductOutput;
import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductPatchInput;
import id.ac.ub.abl.product.adapter.inbound.rest.mapper.ProductHttpMapper;
import id.ac.ub.abl.product.usecase.service.CreateProductUseCase;
import id.ac.ub.abl.product.usecase.service.DeleteProductUseCase;
import id.ac.ub.abl.product.usecase.service.GetProductUseCase;
import id.ac.ub.abl.product.usecase.service.ListProductsUseCase;
import id.ac.ub.abl.product.usecase.service.PatchProductUseCase;
import id.ac.ub.abl.product.usecase.service.ReplaceProductUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class ProductsApiController implements ProductsApi {
    private final CreateProductUseCase create;
    private final ListProductsUseCase list;
    private final GetProductUseCase get;
    private final ReplaceProductUseCase replace;
    private final PatchProductUseCase patch;
    private final DeleteProductUseCase delete;
    private final ProductHttpMapper mapper;

    public ProductsApiController(CreateProductUseCase create, ListProductsUseCase list,
                                 GetProductUseCase get, ReplaceProductUseCase replace,
                                 PatchProductUseCase patch, DeleteProductUseCase delete,
                                 ProductHttpMapper mapper) {
        this.create = create;
        this.list = list;
        this.get = get;
        this.replace = replace;
        this.patch = patch;
        this.delete = delete;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<ProductOutput>> getProducts() {
        return ResponseEntity.ok(list.execute().stream().map(mapper::toOutput).toList());
    }

    @Override
    public ResponseEntity<ProductOutput> getProductById(Long productId) {
        return ResponseEntity.ok(mapper.toOutput(get.execute(productId)));
    }

    @Override
    public ResponseEntity<ProductOutput> createProduct(ProductInput input) {
        var output = mapper.toOutput(create.execute(mapper.toCreateCommand(input)));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{productId}").buildAndExpand(output.id()).toUri();
        return ResponseEntity.created(location).body(output);
    }

    @Override
    public ResponseEntity<ProductOutput> replaceProduct(Long productId, ProductInput input) {
        return ResponseEntity.ok(mapper.toOutput(replace.execute(mapper.toReplaceCommand(productId, input))));
    }

    @Override
    public ResponseEntity<ProductOutput> updateProduct(Long productId, ProductPatchInput input) {
        return ResponseEntity.ok(mapper.toOutput(patch.execute(mapper.toPatchCommand(productId, input))));
    }

    @Override
    public ResponseEntity<Void> deleteProduct(Long productId) {
        delete.execute(productId);
        return ResponseEntity.noContent().build();
    }
}
