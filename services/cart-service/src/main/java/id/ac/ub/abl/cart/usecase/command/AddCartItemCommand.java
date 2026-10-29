package id.ac.ub.abl.cart.usecase.command;

/** Input aplikasi untuk menambahkan produk ke keranjang. */
public record AddCartItemCommand(Long cartId, Long productId, int quantity) {
}
