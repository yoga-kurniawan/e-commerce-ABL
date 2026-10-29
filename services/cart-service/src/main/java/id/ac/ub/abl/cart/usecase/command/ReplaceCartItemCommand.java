package id.ac.ub.abl.cart.usecase.command;

/** Input aplikasi untuk mengganti quantity item dalam keranjang. */
public record ReplaceCartItemCommand(Long cartId, Long productId, int quantity) {
}
