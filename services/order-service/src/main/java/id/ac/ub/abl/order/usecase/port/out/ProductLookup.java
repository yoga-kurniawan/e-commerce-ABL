package id.ac.ub.abl.order.usecase.port.out;

import java.util.Optional;

import id.ac.ub.abl.order.domain.ProductSnapshot;

/** Port pencarian produk aktif dari Product Service. */
public interface ProductLookup {
    /** Kosong bila produk tidak ada atau nonaktif; kegagalan koneksi dilaporkan sebagai exception. */
    Optional<ProductSnapshot> findById(Long productId);
}
