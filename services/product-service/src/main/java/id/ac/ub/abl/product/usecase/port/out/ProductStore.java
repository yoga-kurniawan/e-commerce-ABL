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

    Optional<Product> findById(Long productId);

    /** Mengembalikan daftar kosong jika belum ada produk. */
    List<Product> findAll();

    /** Menghapus produk yang sebelumnya telah ditemukan oleh use case. */
    void delete(Product product);
}
