package id.ac.ub.abl.order.usecase.exception;

/** customerId request tidak sama dengan referensi customer pada Cart; bukan pemeriksaan autentikasi. */
public class CartOwnershipMismatchException extends RuntimeException {
    public CartOwnershipMismatchException(Long cartId, Long customerId) {
        super("Keranjang dengan ID " + cartId + " bukan milik customer " + customerId);
    }
}
