package id.ac.ub.abl.order.domain;

import java.math.BigDecimal;

import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;

/** Item Cart; unitPrice berasal dari Cart v2 (atau fallback eksak subtotal / quantity). */
public record CartItemSnapshot(Long productId, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    public CartItemSnapshot {
        if (productId == null) {
            throw new DomainRuleViolation("ID produk item keranjang wajib diisi");
        }
        if (quantity < 1) {
            throw new DomainRuleViolation("Quantity item keranjang minimal 1");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new DomainRuleViolation("Harga satuan item keranjang tidak boleh negatif");
        }
        if (subtotal == null || subtotal.signum() < 0) {
            throw new DomainRuleViolation("Subtotal item keranjang tidak boleh negatif");
        }
    }

    /** Overload 3-arg untuk kompatibilitas unit test yang sudah ada. */
    public CartItemSnapshot(Long productId, int quantity, BigDecimal subtotal) {
        this(productId, quantity, computeUnitPrice(quantity, subtotal), subtotal);
    }

    /** Membagi subtotal dengan quantity dan mengubah ArithmeticException menjadi DomainRuleViolation. */
    public static BigDecimal computeUnitPrice(int quantity, BigDecimal subtotal) {
        if (quantity < 1) {
            throw new DomainRuleViolation("Quantity item keranjang minimal 1");
        }
        if (subtotal == null || subtotal.signum() < 0) {
            throw new DomainRuleViolation("Subtotal item keranjang tidak boleh negatif");
        }
        try {
            return subtotal.divide(BigDecimal.valueOf(quantity));
        } catch (ArithmeticException ex) {
            throw new DomainRuleViolation("Subtotal item keranjang tidak habis dibagi quantity");
        }
    }
}
