package id.ac.ub.abl.order.usecase.exception;

/** Pembatalan ditolak karena status Payment pesanan (DEC-023). */
public class OrderCancellationConflictException extends RuntimeException {
    public OrderCancellationConflictException(String message) {
        super(message);
    }
}
