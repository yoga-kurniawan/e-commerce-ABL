package id.ac.ub.abl.order.adapter.inbound.rest.dto;

import jakarta.validation.constraints.NotNull;

/** Request POST /orders untuk membuat pesanan dari keranjang. */
public record OrderInput(
        @NotNull(message = "ID keranjang wajib diisi")
        Long cartId,

        @NotNull(message = "ID customer wajib diisi")
        Long customerId) {
}
