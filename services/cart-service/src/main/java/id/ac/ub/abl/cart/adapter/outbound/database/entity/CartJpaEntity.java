package id.ac.ub.abl.cart.adapter.outbound.database.entity;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** Mapping penyimpanan Cart; aturan bisnis tetap berada pada domain Cart. */
@Entity
@Table(name = "carts")
public class CartJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CartItemJpaEntity> items = new ArrayList<>();

    protected CartJpaEntity() {
    }

    public CartJpaEntity(Long id, Long customerId) {
        this.id = id;
        this.customerId = customerId;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public List<CartItemJpaEntity> getItems() { return items; }

    public void addItem(CartItemJpaEntity item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(CartItemJpaEntity item) {
        items.remove(item);
        item.setCart(null);
    }
}
