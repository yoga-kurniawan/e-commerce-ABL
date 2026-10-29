package id.ac.ub.abl.order.usecase.port.out;

import java.util.Optional;

import id.ac.ub.abl.order.domain.CartSnapshot;

/** Port pembacaan keranjang dari Cart Service. */
public interface CartLookup {
    /** Kosong bila Cart tidak ditemukan; kegagalan koneksi dilaporkan sebagai exception. */
    Optional<CartSnapshot> findById(Long cartId);
}
