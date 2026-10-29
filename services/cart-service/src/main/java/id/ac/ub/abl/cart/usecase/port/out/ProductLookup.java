package id.ac.ub.abl.cart.usecase.port.out;

import java.util.Optional;
import id.ac.ub.abl.cart.domain.ProductSnapshot;

/** Port pencarian produk eksternal dari Product Service. */
public interface ProductLookup {
    /** Mengambil snapshot produk aktif berdasarkan ID produk. */
    Optional<ProductSnapshot> findById(Long productId);
}
