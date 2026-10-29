package id.ac.ub.abl.cart.adapter.inbound.rest;

import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemPatchInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemQuantityInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartOutput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.UpdatedCartItemOutput;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** Kontrak HTTP; annotation route didefinisikan pada interface ini. */
@RequestMapping(value = "/api/v1/carts", produces = MediaType.APPLICATION_JSON_VALUE)
public interface CartsApi {

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CartOutput> createCart(@Valid @RequestBody CartInput input);

    @GetMapping("/{cartId}")
    ResponseEntity<CartOutput> getCart(@PathVariable("cartId") Long cartId);

    @PostMapping(value = "/{cartId}/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CartOutput> addItem(@PathVariable("cartId") Long cartId,
                                       @Valid @RequestBody CartItemInput input);

    @PutMapping(value = "/{cartId}/items/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UpdatedCartItemOutput> replaceItem(@PathVariable("cartId") Long cartId,
                                                      @PathVariable("productId") Long productId,
                                                      @Valid @RequestBody CartItemQuantityInput input);

    @PatchMapping(value = "/{cartId}/items/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UpdatedCartItemOutput> updateItem(@PathVariable("cartId") Long cartId,
                                                    @PathVariable("productId") Long productId,
                                                    @Valid @RequestBody CartItemPatchInput input);

    @DeleteMapping("/{cartId}/items/{productId}")
    ResponseEntity<Void> deleteItem(@PathVariable("cartId") Long cartId,
                                   @PathVariable("productId") Long productId);
}
