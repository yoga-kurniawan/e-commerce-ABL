package id.ac.ub.abl.order.usecase.exception;

/** Produk dalam Cart sudah tidak ada atau nonaktif pada katalog. */
public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(Long productId) {
        super("Produk dengan ID " + productId + " tidak lagi tersedia");
    }
}
