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
@RequestMapping(value = "/api/v2/carts", produces = MediaType.APPLICATION_JSON_VALUE)
public interface CartsApiV2 {

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CartOutputV2> createCart(@Valid @RequestBody CartInput input);

    @GetMapping("/{cartId}")
    ResponseEntity<CartOutputV2> getCart(@PathVariable("cartId") Long cartId);

    @PostMapping(value = "/{cartId}/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CartOutputV2> addItem(@PathVariable("cartId") Long cartId,
                                         @Valid @RequestBody CartItemInput input);

    @PutMapping(value = "/{cartId}/items/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UpdatedCartItemOutputV2> replaceItem(@PathVariable("cartId") Long cartId,
                                                        @PathVariable("productId") Long productId,
                                                        @Valid @RequestBody CartItemQuantityInput input);

    @PatchMapping(value = "/{cartId}/items/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UpdatedCartItemOutputV2> updateItem(@PathVariable("cartId") Long cartId,
                                                      @PathVariable("productId") Long productId,
                                                      @Valid @RequestBody CartItemPatchInput input);

    @DeleteMapping("/{cartId}/items/{productId}")
    ResponseEntity<Void> deleteItem(@PathVariable("cartId") Long cartId,
                                   @PathVariable("productId") Long productId);

    @DeleteMapping("/{cartId}/items")
    ResponseEntity<Void> clearItems(@PathVariable("cartId") Long cartId);
}
