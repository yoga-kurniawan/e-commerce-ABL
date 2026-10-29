package id.ac.ub.abl.order.adapter.outbound.database.entity;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import id.ac.ub.abl.order.domain.OrderStatus;

/** Mapping penyimpanan Order; tabel memakai nama orders, bukan keyword SQL order. */
@Entity
@Table(name = "orders")
public class OrderJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "shipping_address", columnDefinition = "text")
    private String shippingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    public OrderJpaEntity(Long id, Long customerId, Long cartId, OrderStatus status, String shippingAddress) {
        this.id = id;
        this.customerId = customerId;
        this.cartId = cartId;
        this.status = status;
        this.shippingAddress = shippingAddress;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public Long getCartId() { return cartId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public List<OrderItemJpaEntity> getItems() { return items; }

    public void addItem(OrderItemJpaEntity item) {
        items.add(item);
        item.setOrder(this);
    }
}
