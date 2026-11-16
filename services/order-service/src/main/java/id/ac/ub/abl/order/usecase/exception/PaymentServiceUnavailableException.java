package id.ac.ub.abl.order.usecase.exception;

public class PaymentServiceUnavailableException extends RuntimeException {
    public PaymentServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
