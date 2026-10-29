package id.ac.ub.abl.product.adapter.inbound.rest.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Request POST/PUT; seluruh atribut wajib disertakan. */
public record ProductInput(
        @NotNull(message = "Nama produk wajib diisi") String name,
        @NotNull(message = "Harga produk wajib diisi")
        @PositiveOrZero(message = "Harga produk tidak boleh negatif") BigDecimal price,
        @NotNull(message = "Stok produk wajib diisi")
        @PositiveOrZero(message = "Stok produk tidak boleh negatif") Integer stock) {
}
