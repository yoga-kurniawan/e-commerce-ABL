package id.ac.ub.abl.product.usecase.port.out;

import java.util.List;
import java.util.Optional;

import id.ac.ub.abl.product.domain.Product;

/** Port penyimpanan lokal Product. Tidak mengekspos tipe JPA atau HTTP. */
public interface ProductStore {
    /**
     * Menyimpan produk baru atau perubahan produk yang sudah ada.
     * Hasil menyertakan ID dari penyimpanan untuk produk baru.
     */
    Product save(Product product);

    /** Hanya mencari produk aktif; produk nonaktif dianggap tidak ditemukan. */
    Optional<Product> findById(Long productId);

    /** Mengembalikan produk aktif, atau daftar kosong jika tidak ada. */
    List<Product> findAll();

    /** Menyimpan produk sebagai nonaktif tanpa menghapus catatan database. */
    void delete(Product product);
}
