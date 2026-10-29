package id.ac.ub.abl.product.adapter.inbound.rest;

import java.util.List;

import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductInput;
import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductOutput;
import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductPatchInput;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Kontrak HTTP; annotation route hanya didefinisikan pada interface ini. */
@RequestMapping(value = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
public interface ProductsApi {
    @GetMapping
    ResponseEntity<List<ProductOutput>> getProducts();

    @GetMapping("/{productId}")
    ResponseEntity<ProductOutput> getProductById(@PathVariable("productId") Long productId);

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProductOutput> createProduct(@Valid @RequestBody ProductInput input);

    @PutMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProductOutput> replaceProduct(@PathVariable("productId") Long productId,
                                               @Valid @RequestBody ProductInput input);

    @PatchMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProductOutput> updateProduct(@PathVariable("productId") Long productId,
                                              @Valid @RequestBody ProductPatchInput input);

    @DeleteMapping("/{productId}")
    ResponseEntity<Void> deleteProduct(@PathVariable("productId") Long productId);
}
