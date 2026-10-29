package id.ac.ub.abl.order.usecase.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("Pesanan dengan ID " + orderId + " tidak ditemukan");
    }
}
