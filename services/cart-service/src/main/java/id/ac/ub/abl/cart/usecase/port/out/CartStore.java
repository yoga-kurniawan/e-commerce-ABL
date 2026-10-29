package id.ac.ub.abl.cart.usecase.port.out;

import java.util.Optional;
import id.ac.ub.abl.cart.domain.Cart;

/** Port penyimpanan lokal Cart. */
public interface CartStore {
    /**
     * Menyimpan keranjang baru atau perubahan pada keranjang yang sudah ada.
     * Hasil menyertakan ID dari penyimpanan untuk keranjang baru.
     */
    Cart save(Cart cart);

    /** Mencari keranjang berdasarkan ID. */
    Optional<Cart> findById(Long cartId);

    /** Menghapus keranjang. */
    void delete(Cart cart);
}
