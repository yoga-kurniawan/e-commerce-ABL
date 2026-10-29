package id.ac.ub.abl.order.usecase.exception;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException(Long cartId) {
        super("Keranjang dengan ID " + cartId + " masih kosong");
    }
}
