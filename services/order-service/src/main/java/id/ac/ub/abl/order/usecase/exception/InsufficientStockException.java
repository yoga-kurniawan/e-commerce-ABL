package id.ac.ub.abl.order.usecase.exception;

/** Pemeriksaan saat checkout; bukan reservasi stok (OPEN-004). */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(Long productId) {
        super("Stok produk dengan ID " + productId + " tidak mencukupi");
    }
}
