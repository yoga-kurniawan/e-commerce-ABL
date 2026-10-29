package id.ac.ub.abl.cart.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import id.ac.ub.abl.cart.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.cart.domain.exception.InsufficientStockException;

/** Aggregate root untuk keranjang belanja. Mengelola item, subtotal, dan total. */
public final class Cart {
    private final Long id;
    private final Long customerId;
    private final List<CartItem> items;
    private final BigDecimal total;

    private Cart(Long id, Long customerId, List<CartItem> items) {
        if (customerId == null) {
            throw new DomainRuleViolation("ID customer wajib diisi");
        }
        this.id = id;
        this.customerId = customerId;
        this.items = items != null ? List.copyOf(items) : List.of();
        this.total = calculateTotal(this.items);
    }

    private static BigDecimal calculateTotal(List<CartItem> items) {
        return items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Membuat keranjang baru kosong tanpa ID (ID diberikan oleh penyimpanan). */
    public static Cart create(Long customerId) {
        return new Cart(null, customerId, List.of());
    }

    /** Memulihkan keranjang yang sudah tersimpan dari database. */
    public static Cart restore(Long id, Long customerId, List<CartItem> items) {
        if (id == null) {
            throw new DomainRuleViolation("ID keranjang tersimpan wajib diisi");
        }
        return new Cart(id, customerId, items);
    }

    /**
     * Menambahkan produk ke keranjang.
     * Jika produk sudah ada, quantity ditambahkan (OPEN-002 A).
     * Validasi stok menggunakan quantity akhir.
     * Harga satuan saat penambahan pertama dipertahankan (OPEN-003 B).
     */
    public Cart addItem(ProductSnapshot product, int quantity) {
        if (quantity < 1) {
            throw new DomainRuleViolation("Quantity minimal 1");
        }
        List<CartItem> updatedItems = new ArrayList<>(this.items);
        Optional<CartItem> existingItemOpt = findItem(product.id());

        if (existingItemOpt.isPresent()) {
            CartItem existing = existingItemOpt.get();
            int newQuantity = existing.getQuantity() + quantity;
            if (newQuantity > product.stock()) {
                throw new InsufficientStockException("Jumlah melebihi stok yang tersedia");
            }
            int index = updatedItems.indexOf(existing);
            updatedItems.set(index, existing.withQuantity(newQuantity));
        } else {
            if (quantity > product.stock()) {
                throw new InsufficientStockException("Jumlah melebihi stok yang tersedia");
            }
            updatedItems.add(CartItem.create(product.id(), quantity, product.price()));
        }

        return new Cart(this.id, this.customerId, updatedItems);
    }

    /**
     * Mengganti quantity produk dalam keranjang (PUT/PATCH).
     * Harga satuan awal tetap dipertahankan.
     */
    public Cart replaceItemQuantity(Long productId, int targetQuantity, int availableStock) {
        if (targetQuantity < 1) {
            throw new DomainRuleViolation("Quantity minimal 1");
        }
        if (targetQuantity > availableStock) {
            throw new InsufficientStockException("Jumlah melebihi stok yang tersedia");
        }
        CartItem existing = findItem(productId)
                .orElseThrow(() -> new DomainRuleViolation("Item produk dengan ID " + productId + " tidak ditemukan dalam keranjang"));

        List<CartItem> updatedItems = new ArrayList<>(this.items);
        int index = updatedItems.indexOf(existing);
        updatedItems.set(index, existing.withQuantity(targetQuantity));

        return new Cart(this.id, this.customerId, updatedItems);
    }

    /** Menghapus produk tertentu dari keranjang. */
    public Cart removeItem(Long productId) {
        CartItem existing = findItem(productId)
                .orElseThrow(() -> new DomainRuleViolation("Item produk dengan ID " + productId + " tidak ditemukan dalam keranjang"));

        List<CartItem> updatedItems = new ArrayList<>(this.items);
        updatedItems.remove(existing);

        return new Cart(this.id, this.customerId, updatedItems);
    }

    public Optional<CartItem> findItem(Long productId) {
        return items.stream()
                .filter(item -> Objects.equals(item.getProductId(), productId))
                .findFirst();
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public List<CartItem> getItems() { return Collections.unmodifiableList(items); }
    public BigDecimal getTotal() { return total; }
}
