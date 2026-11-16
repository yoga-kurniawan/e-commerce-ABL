package id.ac.ub.abl.product.usecase.command;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Input perubahan sebagian produk. Gunakan PatchField.absent() untuk field
 * yang tidak dikirim. Kebijakan nilai null diterapkan saat command diproses.
 */
public record PatchProductCommand(
        Long productId,
        PatchField<String> name,
        PatchField<BigDecimal> price,
        PatchField<Integer> stock,
        PatchField<String> description) {

    public PatchProductCommand(Long productId, PatchField<String> name, PatchField<BigDecimal> price,
                               PatchField<Integer> stock) {
        this(productId, name, price, stock, PatchField.absent());
    }

    public PatchProductCommand {
        Objects.requireNonNull(name, "Gunakan PatchField.absent() untuk nama yang tidak dikirim");
        Objects.requireNonNull(price, "Gunakan PatchField.absent() untuk harga yang tidak dikirim");
        Objects.requireNonNull(stock, "Gunakan PatchField.absent() untuk stok yang tidak dikirim");
        Objects.requireNonNull(description);
    }

    public boolean hasChanges() {
        return name.supplied() || price.supplied() || stock.supplied() || description.supplied();
    }
}
