package id.ac.ub.abl.product.usecase.command;

import java.math.BigDecimal;

/** Input aplikasi untuk mengganti seluruh atribut produk yang dapat diubah. */
public record ReplaceProductCommand(Long productId, String name, BigDecimal price, Integer stock,
                                    PatchField<String> description) {
    public ReplaceProductCommand {
        java.util.Objects.requireNonNull(description);
    }

    public ReplaceProductCommand(Long productId, String name, BigDecimal price, Integer stock) {
        this(productId, name, price, stock, PatchField.absent());
    }
}
