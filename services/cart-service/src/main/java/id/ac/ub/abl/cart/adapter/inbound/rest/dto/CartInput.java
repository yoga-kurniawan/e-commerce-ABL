package id.ac.ub.abl.cart.adapter.inbound.rest.dto;

import jakarta.validation.constraints.NotNull;

/** Request POST /carts untuk membuat keranjang baru kosong. */
public record CartInput(
        @NotNull(message = "ID customer wajib diisi")
        Long customerId) {
}
