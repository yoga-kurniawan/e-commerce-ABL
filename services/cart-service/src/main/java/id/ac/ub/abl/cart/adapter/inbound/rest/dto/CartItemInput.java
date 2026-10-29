package id.ac.ub.abl.cart.adapter.inbound.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Request POST /carts/{cartId}/items untuk menambahkan produk ke keranjang. */
public record CartItemInput(
        @NotNull(message = "ID produk wajib diisi")
        Long productId,

        @NotNull(message = "Quantity wajib diisi")
        @Min(value = 1, message = "Quantity minimal 1")
        Integer quantity) {
}
