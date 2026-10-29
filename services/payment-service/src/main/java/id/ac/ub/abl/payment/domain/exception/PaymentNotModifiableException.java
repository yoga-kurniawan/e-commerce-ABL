package id.ac.ub.abl.payment.domain.exception;

public class PaymentNotModifiableException extends RuntimeException {
    public PaymentNotModifiableException() {
        super("Reference hanya dapat diubah selama Payment PENDING");
    }
}
