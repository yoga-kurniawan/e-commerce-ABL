package id.ac.ub.abl.product.adapter.outbound.database.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Mapping penyimpanan; aturan bisnis tetap berada pada domain Product. */
@Entity
@Table(name = "products")
public class ProductJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(nullable = false, columnDefinition = "numeric")
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 1000)
    private String description;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ProductJpaEntity() {
    }

    public ProductJpaEntity(Long id, String name, BigDecimal price, Integer stock) {
        this(id, name, price, stock, true);
    }

    public ProductJpaEntity(Long id, String name, BigDecimal price, Integer stock, boolean active) {
        this(id, name, price, stock, active, null);
    }

    public ProductJpaEntity(Long id, String name, BigDecimal price, Integer stock, boolean active, String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.active = active;
        this.description = description;
    }

    /** Memperbarui entity managed agar @Version optimistic locking terjaga tanpa merge overwrite. */
    public void updateState(String name, BigDecimal price, Integer stock, boolean active, String description) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.active = active;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public Integer getStock() { return stock; }
    public boolean isActive() { return active; }
    public String getDescription() { return description; }
    public Long getVersion() { return version; }
}
