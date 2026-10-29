package id.ac.ub.abl.order.usecase.exception;

public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
