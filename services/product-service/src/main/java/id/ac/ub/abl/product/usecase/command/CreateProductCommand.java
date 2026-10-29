package id.ac.ub.abl.product.usecase.command;

import java.math.BigDecimal;

/** Input aplikasi untuk membuat produk; aturan bisnis diperiksa oleh domain. */
public record CreateProductCommand(String name, BigDecimal price, Integer stock) {
}
