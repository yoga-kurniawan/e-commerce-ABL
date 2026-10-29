package id.ac.ub.abl.cart.domain;

import java.math.BigDecimal;
import java.util.Objects;
import id.ac.ub.abl.cart.domain.exception.DomainRuleViolation;

/** Item individual dalam keranjang belanja. Menyimpan harga satuan saat produk masuk keranjang. */
public final class CartItem {
    private final Long productId;
    private final int quantity;
    private final BigDecimal unitPrice;

    private CartItem(Long productId, Integer quantity, BigDecimal unitPrice) {
        if (productId == null) {
            throw new DomainRuleViolation("ID produk wajib diisi");
        }
        if (quantity == null || quantity < 1) {
            throw new DomainRuleViolation("Quantity item minimal 1");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new DomainRuleViolation("Harga satuan tidak boleh negatif");
        }
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public static CartItem create(Long productId, int quantity, BigDecimal unitPrice) {
        return new CartItem(productId, quantity, unitPrice);
    }

    public static CartItem restore(Long productId, int quantity, BigDecimal unitPrice) {
        return new CartItem(productId, quantity, unitPrice);
    }

    public CartItem withQuantity(int newQuantity) {
        return new CartItem(this.productId, newQuantity, this.unitPrice);
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CartItem cartItem)) return false;
        return quantity == cartItem.quantity &&
                Objects.equals(productId, cartItem.productId) &&
                Objects.equals(unitPrice, cartItem.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, quantity, unitPrice);
    }
}
