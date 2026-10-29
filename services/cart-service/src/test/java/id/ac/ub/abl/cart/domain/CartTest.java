package id.ac.ub.abl.cart.domain;

import java.math.BigDecimal;
import java.util.List;

import id.ac.ub.abl.cart.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.cart.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CartTest {

    @Test
    void createsEmptyCartWithZeroTotal() {
        Cart cart = Cart.create(101L);

        assertNull(cart.getId());
        assertEquals(101L, cart.getCustomerId());
        assertTrue(cart.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, cart.getTotal());
    }

    @Test
    void rejectsCreationWithoutCustomerId() {
        assertThrows(DomainRuleViolation.class, () -> Cart.create(null));
        assertThrows(DomainRuleViolation.class, () -> Cart.restore(1L, null, List.of()));
        assertThrows(DomainRuleViolation.class, () -> Cart.restore(null, 101L, List.of()));
    }

    @Test
    void addNewItemCalculatesSubtotalAndTotal() {
        Cart cart = Cart.create(101L);
        ProductSnapshot product = new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10);

        Cart updated = cart.addItem(product, 2);

        assertEquals(1, updated.getItems().size());
        CartItem item = updated.getItems().get(0);
        assertEquals(1L, item.getProductId());
        assertEquals(2, item.getQuantity());
        assertEquals(new BigDecimal("350000"), item.getUnitPrice());
        assertEquals(new BigDecimal("700000"), item.getSubtotal());
        assertEquals(new BigDecimal("700000"), updated.getTotal());
    }

    @Test
    void addExistingItemIncreasesQuantityAndPreservesOriginalPrice() {
        // OPEN-002 A: menambah quantity jika produk sudah ada
        // OPEN-003 B: harga satuan awal dipertahankan meski katalog berubah
        Cart cart = Cart.create(101L);
        ProductSnapshot initialProduct = new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10);
        Cart step1 = cart.addItem(initialProduct, 2);

        ProductSnapshot changedProduct = new ProductSnapshot(1L, "Keyboard", new BigDecimal("400000"), 10);
        Cart step2 = step1.addItem(changedProduct, 3);

        assertEquals(1, step2.getItems().size());
        CartItem item = step2.getItems().get(0);
        assertEquals(1L, item.getProductId());
        assertEquals(5, item.getQuantity());
        assertEquals(new BigDecimal("350000"), item.getUnitPrice());
        assertEquals(new BigDecimal("1750000"), item.getSubtotal());
        assertEquals(new BigDecimal("1750000"), step2.getTotal());
    }

    @Test
    void rejectsAddingItemWhenQuantityExceedsStock() {
        Cart cart = Cart.create(101L);
        ProductSnapshot product = new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 5);

        assertThrows(InsufficientStockException.class, () -> cart.addItem(product, 6));

        // When existing quantity + new quantity exceeds stock
        Cart step1 = cart.addItem(product, 3);
        assertThrows(InsufficientStockException.class, () -> step1.addItem(product, 3));
    }

    @Test
    void rejectsZeroOrNegativeQuantityWhenAdding() {
        Cart cart = Cart.create(101L);
        ProductSnapshot product = new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10);

        assertThrows(DomainRuleViolation.class, () -> cart.addItem(product, 0));
        assertThrows(DomainRuleViolation.class, () -> cart.addItem(product, -1));
    }

    @Test
    void replaceItemQuantityUpdatesSubtotalAndTotal() {
        Cart cart = Cart.create(101L);
        ProductSnapshot product = new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10);
        Cart step1 = cart.addItem(product, 2);

        Cart updated = step1.replaceItemQuantity(1L, 4, 10);

        assertEquals(1, updated.getItems().size());
        CartItem item = updated.getItems().get(0);
        assertEquals(4, item.getQuantity());
        assertEquals(new BigDecimal("1400000"), item.getSubtotal());
        assertEquals(new BigDecimal("1400000"), updated.getTotal());
    }

    @Test
    void replaceItemQuantityRejectsWhenTargetExceedsStock() {
        Cart cart = Cart.create(101L);
        ProductSnapshot product = new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 5);
        Cart step1 = cart.addItem(product, 2);

        assertThrows(InsufficientStockException.class, () -> step1.replaceItemQuantity(1L, 6, 5));
    }

    @Test
    void removeItemRemovesItemAndRecalculatesTotal() {
        Cart cart = Cart.create(101L);
        ProductSnapshot p1 = new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10);
        ProductSnapshot p2 = new ProductSnapshot(2L, "Mouse", new BigDecimal("150000"), 10);
        Cart step1 = cart.addItem(p1, 2).addItem(p2, 1);
        assertEquals(new BigDecimal("850000"), step1.getTotal());

        Cart step2 = step1.removeItem(1L);

        assertEquals(1, step2.getItems().size());
        assertEquals(2L, step2.getItems().get(0).getProductId());
        assertEquals(new BigDecimal("150000"), step2.getTotal());
    }

    @Test
    void removeItemThrowsWhenProductNotInCart() {
        Cart cart = Cart.create(101L);
        assertThrows(DomainRuleViolation.class, () -> cart.removeItem(999L));
    }
}
