package id.ac.ub.abl.cart.usecase.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long productId) {
        super("Produk dengan ID " + productId + " tidak ditemukan");
    }
}
