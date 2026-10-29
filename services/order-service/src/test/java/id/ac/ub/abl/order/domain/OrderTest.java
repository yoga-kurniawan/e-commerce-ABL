package id.ac.ub.abl.order.domain;

import java.math.BigDecimal;
import java.util.List;

import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.order.domain.exception.OrderNotModifiableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    private static final OrderItem KEYBOARD = OrderItem.create(1L, "Keyboard", 2, new BigDecimal("350000"));
    private static final OrderItem MOUSE = OrderItem.create(2L, "Mouse", 1, new BigDecimal("150000.50"));

    @Test
    void placeCreatesPendingOrderWithTotalFromItems() {
        Order order = Order.place(101L, 10L, List.of(KEYBOARD, MOUSE));

        assertNull(order.getId());
        assertEquals(101L, order.getCustomerId());
        assertEquals(10L, order.getCartId());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertNull(order.getShippingAddress());
        assertEquals(2, order.getItems().size());
        assertEquals(0, new BigDecimal("850000.50").compareTo(order.getTotal()));
    }

    @Test
    void placeRejectsMissingReferencesAndEmptyItems() {
        assertThrows(DomainRuleViolation.class, () -> Order.place(null, 10L, List.of(KEYBOARD)));
        assertThrows(DomainRuleViolation.class, () -> Order.place(101L, null, List.of(KEYBOARD)));
        assertThrows(DomainRuleViolation.class, () -> Order.place(101L, 10L, List.of()));
        assertThrows(DomainRuleViolation.class, () -> Order.place(101L, 10L, null));
    }

    @Test
    void placeRejectsDuplicateProduct() {
        OrderItem duplicate = OrderItem.create(1L, "Keyboard", 1, new BigDecimal("350000"));
        assertThrows(DomainRuleViolation.class, () -> Order.place(101L, 10L, List.of(KEYBOARD, duplicate)));
    }

    @Test
    void itemsAreImmutable() {
        Order order = Order.place(101L, 10L, List.of(KEYBOARD));
        assertThrows(UnsupportedOperationException.class, () -> order.getItems().add(MOUSE));
    }

    @Test
    void restoreRequiresId() {
        assertThrows(DomainRuleViolation.class, () ->
                Order.restore(null, 101L, 10L, OrderStatus.PENDING, null, List.of(KEYBOARD)));
        assertThrows(DomainRuleViolation.class, () ->
                Order.restore(1L, 101L, 10L, null, null, List.of(KEYBOARD)));
    }

    @Test
    void changeShippingAddressWhilePendingKeepsTotalAndStatus() {
        Order order = Order.restore(501L, 101L, 10L, OrderStatus.PENDING, null, List.of(KEYBOARD));

        Order changed = order.changeShippingAddress("Jl. Veteran No. 10, Malang");

        assertEquals("Jl. Veteran No. 10, Malang", changed.getShippingAddress());
        assertEquals(OrderStatus.PENDING, changed.getStatus());
        assertEquals(order.getTotal(), changed.getTotal());
        assertEquals(501L, changed.getId());
        assertNull(order.getShippingAddress());
    }

    @Test
    void changeShippingAddressRejectsBlankValue() {
        Order order = Order.restore(501L, 101L, 10L, OrderStatus.PENDING, null, List.of(KEYBOARD));
        assertThrows(DomainRuleViolation.class, () -> order.changeShippingAddress(null));
        assertThrows(DomainRuleViolation.class, () -> order.changeShippingAddress("   "));
    }

    @Test
    void changeShippingAddressRejectedOutsidePending() {
        for (OrderStatus status : List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)) {
            Order order = Order.restore(501L, 101L, 10L, status, "Alamat lama", List.of(KEYBOARD));
            assertThrows(OrderNotModifiableException.class, () -> order.changeShippingAddress("Alamat baru"));
        }
    }

    @Test
    void orderItemValidatesFieldsAndComputesSubtotal() {
        assertEquals(0, new BigDecimal("700000").compareTo(KEYBOARD.getSubtotal()));
        assertThrows(DomainRuleViolation.class, () -> OrderItem.create(null, "X", 1, BigDecimal.ONE));
        assertThrows(DomainRuleViolation.class, () -> OrderItem.create(1L, " ", 1, BigDecimal.ONE));
        assertThrows(DomainRuleViolation.class, () -> OrderItem.create(1L, "X", 0, BigDecimal.ONE));
        assertThrows(DomainRuleViolation.class, () -> OrderItem.create(1L, "X", 1, new BigDecimal("-1")));
        assertThrows(DomainRuleViolation.class, () -> OrderItem.create(1L, "X", 1, null));
    }

    @Test
    void cartItemSnapshotDerivesStoredUnitPriceFromSubtotal() {
        CartItemSnapshot item = new CartItemSnapshot(1L, 3, new BigDecimal("1050000.375"));
        assertEquals(0, new BigDecimal("350000.125").compareTo(item.unitPrice()));
        assertThrows(DomainRuleViolation.class, () -> new CartItemSnapshot(1L, 0, BigDecimal.TEN));
        assertThrows(DomainRuleViolation.class, () -> new CartItemSnapshot(1L, 1, new BigDecimal("-1")));
    }

    @Test
    void cartSnapshotTreatsMissingItemsAsEmpty() {
        assertTrue(new CartSnapshot(10L, 101L, null).isEmpty());
        assertThrows(DomainRuleViolation.class, () -> new CartSnapshot(null, 101L, List.of()));
        assertThrows(DomainRuleViolation.class, () -> new CartSnapshot(10L, null, List.of()));
    }

    @Test
    void productSnapshotValidatesFields() {
        assertThrows(DomainRuleViolation.class, () -> new ProductSnapshot(1L, "", BigDecimal.ONE, 1));
        assertThrows(DomainRuleViolation.class, () -> new ProductSnapshot(1L, "X", new BigDecimal("-1"), 1));
        assertThrows(DomainRuleViolation.class, () -> new ProductSnapshot(1L, "X", BigDecimal.ONE, -1));
    }
}
