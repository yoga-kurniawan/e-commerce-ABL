package id.ac.ub.abl.payment.domain.exception;

public class OrderNotPayableException extends RuntimeException {
    public OrderNotPayableException(Long orderId) {
        super("Order " + orderId + " tidak dalam status yang dapat dibayar");
    }
}
