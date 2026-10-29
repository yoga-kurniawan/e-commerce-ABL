package id.ac.ub.abl.cart.adapter.outbound.database.entity;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Mapping penyimpanan CartItem; relasi ke CartJpaEntity. */
@Entity
@Table(name = "cart_items")
public class CartItemJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartJpaEntity cart;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, columnDefinition = "numeric")
    private BigDecimal unitPrice;

    protected CartItemJpaEntity() {
    }

    public CartItemJpaEntity(CartJpaEntity cart, Long productId, Integer quantity, BigDecimal unitPrice) {
        this.cart = cart;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() { return id; }
    public CartJpaEntity getCart() { return cart; }
    public void setCart(CartJpaEntity cart) { this.cart = cart; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
