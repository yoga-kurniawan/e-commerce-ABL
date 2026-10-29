package id.ac.ub.abl.order.domain.exception;

/** Perubahan atribut ditolak karena status pesanan tidak mengizinkannya. */
public class OrderNotModifiableException extends RuntimeException {
    public OrderNotModifiableException(String message) {
        super(message);
    }
}
