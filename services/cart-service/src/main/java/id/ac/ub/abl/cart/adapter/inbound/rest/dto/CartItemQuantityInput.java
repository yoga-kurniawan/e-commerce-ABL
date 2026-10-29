package id.ac.ub.abl.cart.adapter.inbound.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Request PUT /carts/{cartId}/items/{productId} untuk mengganti quantity. */
public record CartItemQuantityInput(
        @NotNull(message = "Quantity wajib diisi")
        @Min(value = 1, message = "Quantity minimal 1")
        Integer quantity) {
}
