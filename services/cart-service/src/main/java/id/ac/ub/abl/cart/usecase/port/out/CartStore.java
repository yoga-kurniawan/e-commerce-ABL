package id.ac.ub.abl.cart.usecase.port.out;

import java.util.Optional;
import java.util.function.UnaryOperator;
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

    /**
     * Memuat ulang keranjang dalam satu transaksi lokal singkat, menerapkan mutasi domain,
     * dan menyimpan secara atomik dengan optimistic locking.
     */
    default Optional<Cart> update(Long cartId, UnaryOperator<Cart> mutator) {
        return findById(cartId).map(cart -> save(mutator.apply(cart)));
    }

    /** Menghapus keranjang. */
    void delete(Cart cart);
}
