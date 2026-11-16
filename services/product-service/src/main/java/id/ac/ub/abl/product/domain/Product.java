package id.ac.ub.abl.product.domain;

import java.math.BigDecimal;

import id.ac.ub.abl.product.domain.exception.DomainRuleViolation;

/** Model bisnis produk, tanpa ketergantungan HTTP maupun persistence. */
public final class Product {
    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final int stock;
    private final boolean active;
    private final String description;

    private Product(Long id, String name, BigDecimal price, Integer stock) {
        this(id, name, price, stock, true);
    }

    private Product(Long id, String name, BigDecimal price, Integer stock, boolean active) {
        this(id, name, price, stock, active, null);
    }

    private Product(Long id, String name, BigDecimal price, Integer stock, boolean active, String description) {
        if (name == null) {
            throw new DomainRuleViolation("Nama produk wajib diisi");
        }
        if (price == null || price.signum() < 0) {
            throw new DomainRuleViolation("Harga produk wajib diisi dan tidak boleh negatif");
        }
        if (stock == null || stock < 0) {
            throw new DomainRuleViolation("Stok produk wajib diisi dan tidak boleh negatif");
        }
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.active = active;
        if (description != null && description.length() > 1000) {
            throw new DomainRuleViolation("Deskripsi produk maksimal 1000 karakter");
        }
        this.description = description;
    }

    /** Produk baru belum memiliki ID; ID diberikan oleh penyimpanan. */
    public static Product create(String name, BigDecimal price, Integer stock) {
        return new Product(null, name, price, stock);
    }

    public static Product create(String name, BigDecimal price, Integer stock, String description) {
        return new Product(null, name, price, stock, true, description);
    }

    public static Product restore(Long id, String name, BigDecimal price, Integer stock) {
        return restore(id, name, price, stock, true);
    }

    public static Product restore(Long id, String name, BigDecimal price, Integer stock, boolean active) {
        return restore(id, name, price, stock, active, null);
    }

    public static Product restore(Long id, String name, BigDecimal price, Integer stock, boolean active, String description) {
        if (id == null) {
            throw new DomainRuleViolation("ID produk tersimpan wajib diisi");
        }
        return new Product(id, name, price, stock, active, description);
    }

    public Product replace(String name, BigDecimal price, Integer stock) {
        return new Product(id, name, price, stock, active, description);
    }

    public Product deactivate() {
        return new Product(id, name, price, stock, false, description);
    }

    public Product changeDescription(String description) {
        return new Product(id, name, price, stock, active, description);
    }

    public Product changeName(String name) {
        return replace(name, price, stock);
    }

    public Product changePrice(BigDecimal price) {
        return replace(name, price, stock);
    }

    public Product changeStock(Integer stock) {
        return replace(name, price, stock);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public int getStock() { return stock; }
    public boolean isActive() { return active; }
    public String getDescription() { return description; }
}
