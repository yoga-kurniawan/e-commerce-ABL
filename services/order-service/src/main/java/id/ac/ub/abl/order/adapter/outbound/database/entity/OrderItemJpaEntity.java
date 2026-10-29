package id.ac.ub.abl.order.adapter.outbound.database.entity;

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

/** Mapping snapshot item pesanan; productId hanya referensi, tanpa FK lintas service. */
@Entity
@Table(name = "order_items")
public class OrderItemJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, columnDefinition = "text")
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, columnDefinition = "numeric")
    private BigDecimal unitPrice;

    protected OrderItemJpaEntity() {
    }

    public OrderItemJpaEntity(OrderJpaEntity order, Long productId, String productName,
                              Integer quantity, BigDecimal unitPrice) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() { return id; }
    public OrderJpaEntity getOrder() { return order; }
    public void setOrder(OrderJpaEntity order) { this.order = order; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
