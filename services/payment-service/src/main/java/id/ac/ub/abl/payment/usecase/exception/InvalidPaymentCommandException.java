package id.ac.ub.abl.payment.usecase.exception;

public class InvalidPaymentCommandException extends IllegalArgumentException {
    public InvalidPaymentCommandException(String message) {
        super(message);
    }
}
