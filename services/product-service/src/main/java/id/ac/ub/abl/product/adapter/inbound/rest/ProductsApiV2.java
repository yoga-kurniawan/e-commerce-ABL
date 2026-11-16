package id.ac.ub.abl.product.adapter.inbound.rest;

import java.util.List;
import id.ac.ub.abl.product.adapter.inbound.rest.dto.v2.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping(value = "/api/v2/products", produces = MediaType.APPLICATION_JSON_VALUE)
public interface ProductsApiV2 {
    @GetMapping
    ResponseEntity<List<ProductOutputV2>> getProducts(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size);

    @GetMapping("/{productId}")
    ResponseEntity<ProductOutputV2> getProductById(@PathVariable("productId") Long id);

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProductOutputV2> createProduct(@Valid @RequestBody ProductInputV2 input);

    @PutMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProductOutputV2> replaceProduct(@PathVariable("productId") Long id,
                                                  @Valid @RequestBody ProductInputV2 input);

    @PatchMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProductOutputV2> updateProduct(@PathVariable("productId") Long id,
                                                 @Valid @RequestBody ProductPatchInputV2 input);

    @DeleteMapping("/{productId}")
    ResponseEntity<Void> deleteProduct(@PathVariable("productId") Long id);
}
