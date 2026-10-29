package id.ac.ub.abl.order.domain;

import java.math.BigDecimal;

import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;

/** Data Product aktif yang dibutuhkan Order untuk nama snapshot dan validasi stok. */
public record ProductSnapshot(Long id, String name, BigDecimal price, int stock) {
    public ProductSnapshot {
        if (id == null) {
            throw new DomainRuleViolation("ID produk wajib diisi");
        }
        if (name == null || name.isBlank()) {
            throw new DomainRuleViolation("Nama produk wajib diisi");
        }
        if (price == null || price.signum() < 0) {
            throw new DomainRuleViolation("Harga produk tidak boleh negatif");
        }
        if (stock < 0) {
            throw new DomainRuleViolation("Stok produk tidak boleh negatif");
        }
    }
}
