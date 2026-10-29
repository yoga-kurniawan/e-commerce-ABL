package id.ac.ub.abl.order.usecase.command;

import java.util.Objects;

/** Input aplikasi untuk memperbarui atribut pesanan yang diizinkan. */
public record PatchOrderCommand(Long orderId, PatchField<String> shippingAddress) {
    public PatchOrderCommand {
        Objects.requireNonNull(shippingAddress, "Gunakan PatchField.absent() untuk alamat yang tidak dikirim");
    }

    public boolean hasChanges() {
        return shippingAddress.supplied();
    }
}
