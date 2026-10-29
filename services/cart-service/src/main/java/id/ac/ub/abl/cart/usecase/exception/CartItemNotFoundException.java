package id.ac.ub.abl.cart.usecase.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(Long cartId, Long productId) {
        super("Item produk dengan ID " + productId + " tidak ditemukan dalam keranjang dengan ID " + cartId);
    }
}
