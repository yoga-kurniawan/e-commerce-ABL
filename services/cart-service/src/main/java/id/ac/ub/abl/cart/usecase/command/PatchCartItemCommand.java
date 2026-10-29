package id.ac.ub.abl.cart.usecase.command;

import java.util.Objects;

/** Input aplikasi untuk memperbarui sebagian item dalam keranjang. */
public record PatchCartItemCommand(Long cartId, Long productId, PatchField<Integer> quantity) {
    public PatchCartItemCommand {
        Objects.requireNonNull(quantity, "Gunakan PatchField.absent() untuk quantity yang tidak dikirim");
    }

    public boolean hasChanges() {
        return quantity.supplied();
    }
}
