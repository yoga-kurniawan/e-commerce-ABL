package id.ac.ub.abl.order.usecase.exception;

public class CartServiceUnavailableException extends RuntimeException {
    public CartServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
