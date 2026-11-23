package id.ac.ub.abl.order.usecase.port.out;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import id.ac.ub.abl.order.domain.ProductSnapshot;

/** Port pencarian produk aktif dari Product Service. */
public interface ProductLookup {
    /** Kosong bila produk tidak ada atau nonaktif; kegagalan koneksi dilaporkan sebagai exception. */
    Optional<ProductSnapshot> findById(Long productId);

    /** Mengambil seluruh produk unik sesuai urutan ID; implementasi default memanggil findById secara berurutan. */
    default Map<Long, Optional<ProductSnapshot>> findAllByIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Optional<ProductSnapshot>> result = new LinkedHashMap<>();
        for (Long productId : productIds) {
            result.computeIfAbsent(productId, this::findById);
        }
        return result;
    }
}
