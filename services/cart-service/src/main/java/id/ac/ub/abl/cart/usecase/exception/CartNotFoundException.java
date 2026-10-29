package id.ac.ub.abl.cart.usecase.exception;

public class CartNotFoundException extends RuntimeException {
    public CartNotFoundException(Long cartId) {
        super("Keranjang dengan ID " + cartId + " tidak ditemukan");
    }
}
