package id.ac.ub.abl.order.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.order.domain.exception.OrderNotModifiableException;

/** Aggregate root pesanan. Total dihitung dari snapshot item, bukan input client. */
public final class Order {
    private final Long id;
    private final Long customerId;
    private final Long cartId;
    private final OrderStatus status;
    private final String shippingAddress;
    private final List<OrderItem> items;
    private final BigDecimal total;

    private Order(Long id, Long customerId, Long cartId, OrderStatus status,
                  String shippingAddress, List<OrderItem> items) {
        if (customerId == null) {
            throw new DomainRuleViolation("ID customer wajib diisi");
        }
        if (cartId == null) {
            throw new DomainRuleViolation("ID keranjang wajib diisi");
        }
        if (status == null) {
            throw new DomainRuleViolation("Status pesanan wajib diisi");
        }
        if (items == null || items.isEmpty()) {
            throw new DomainRuleViolation("Pesanan minimal memiliki satu item");
        }
        Set<Long> productIds = new HashSet<>();
        for (OrderItem item : items) {
            if (!productIds.add(item.getProductId())) {
                throw new DomainRuleViolation("Produk dengan ID " + item.getProductId() + " muncul lebih dari sekali");
            }
        }
        if (shippingAddress != null && shippingAddress.isBlank()) {
            throw new DomainRuleViolation("Alamat pengiriman tidak boleh kosong");
        }
        this.id = id;
        this.customerId = customerId;
        this.cartId = cartId;
        this.status = status;
        this.shippingAddress = shippingAddress;
        this.items = List.copyOf(items);
        this.total = this.items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Pesanan baru berstatus PENDING tanpa ID; ID diberikan oleh penyimpanan. */
    public static Order place(Long customerId, Long cartId, List<OrderItem> items) {
        return new Order(null, customerId, cartId, OrderStatus.PENDING, null, items);
    }

    public static Order restore(Long id, Long customerId, Long cartId, OrderStatus status,
                                String shippingAddress, List<OrderItem> items) {
        if (id == null) {
            throw new DomainRuleViolation("ID pesanan tersimpan wajib diisi");
        }
        return new Order(id, customerId, cartId, status, shippingAddress, items);
    }

    /** Alamat hanya dapat diubah sebelum pesanan diproses, yaitu saat PENDING. */
    public Order changeShippingAddress(String newAddress) {
        if (newAddress == null || newAddress.isBlank()) {
            throw new DomainRuleViolation("Alamat pengiriman tidak boleh kosong");
        }
        if (status != OrderStatus.PENDING) {
            throw new OrderNotModifiableException(
                    "Alamat pengiriman tidak dapat diubah pada pesanan berstatus " + status);
        }
        return new Order(id, customerId, cartId, status, newAddress, items);
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public Long getCartId() { return cartId; }
    public OrderStatus getStatus() { return status; }
    public String getShippingAddress() { return shippingAddress; }
    public List<OrderItem> getItems() { return items; }
    public BigDecimal getTotal() { return total; }
}
