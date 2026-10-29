package id.ac.ub.abl.order.domain;

import java.math.BigDecimal;

import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;

/** Item Cart; subtotal berasal dari harga yang disimpan Cart saat produk masuk (OPEN-003 B). */
public record CartItemSnapshot(Long productId, int quantity, BigDecimal subtotal) {
    public CartItemSnapshot {
        if (productId == null) {
            throw new DomainRuleViolation("ID produk item keranjang wajib diisi");
        }
        if (quantity < 1) {
            throw new DomainRuleViolation("Quantity item keranjang minimal 1");
        }
        if (subtotal == null || subtotal.signum() < 0) {
            throw new DomainRuleViolation("Subtotal item keranjang tidak boleh negatif");
        }
    }

    /** Subtotal Cart selalu harga satuan x quantity, sehingga pembagian ini eksak. */
    public BigDecimal unitPrice() {
        return subtotal.divide(BigDecimal.valueOf(quantity));
    }
}
