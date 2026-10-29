package id.ac.ub.abl.order.domain;

import java.math.BigDecimal;
import java.util.Objects;

import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;

/** Snapshot item saat checkout; tidak dihitung ulang dari harga katalog terkini. */
public final class OrderItem {
    private final Long productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;

    private OrderItem(Long productId, String productName, Integer quantity, BigDecimal unitPrice) {
        if (productId == null) {
            throw new DomainRuleViolation("ID produk wajib diisi");
        }
        if (productName == null || productName.isBlank()) {
            throw new DomainRuleViolation("Nama produk wajib diisi");
        }
        if (quantity == null || quantity < 1) {
            throw new DomainRuleViolation("Quantity item minimal 1");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new DomainRuleViolation("Harga satuan tidak boleh negatif");
        }
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public static OrderItem create(Long productId, String productName, int quantity, BigDecimal unitPrice) {
        return new OrderItem(productId, productName, quantity, unitPrice);
    }

    public static OrderItem restore(Long productId, String productName, int quantity, BigDecimal unitPrice) {
        return new OrderItem(productId, productName, quantity, unitPrice);
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem other)) return false;
        return quantity == other.quantity
                && Objects.equals(productId, other.productId)
                && Objects.equals(productName, other.productName)
                && Objects.equals(unitPrice, other.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, productName, quantity, unitPrice);
    }
}
