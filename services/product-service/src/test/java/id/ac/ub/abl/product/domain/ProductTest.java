package id.ac.ub.abl.product.domain;

import java.math.BigDecimal;

import id.ac.ub.abl.product.domain.exception.DomainRuleViolation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductTest {
    @Test
    void deactivationPreservesProductDataAndCannotBeUndoneByReplacement() {
        var original = Product.restore(7L, "Keyboard", new BigDecimal("350000.125"), 20);
        var inactive = original.deactivate();
        assertTrue(original.isActive());
        assertFalse(inactive.isActive());
        assertEquals(original.getId(), inactive.getId());
        assertEquals(original.getName(), inactive.getName());
        assertEquals(original.getPrice(), inactive.getPrice());
        assertEquals(original.getStock(), inactive.getStock());
        assertFalse(inactive.replace("Mouse", BigDecimal.ZERO, 0).isActive());
        assertFalse(inactive.deactivate().isActive());
    }

    @Test
    void createsProductWithoutIdAndAllowsZeroPriceAndStock() {
        Product product = Product.create("Keyboard", BigDecimal.ZERO, 0);

        assertNull(product.getId());
        assertEquals("Keyboard", product.getName());
        assertEquals(BigDecimal.ZERO, product.getPrice());
        assertEquals(0, product.getStock());
    }

    @Test
    void rejectsMissingRequiredAttributes() {
        assertThrows(DomainRuleViolation.class, () -> Product.create(null, BigDecimal.ONE, 1));
        assertThrows(DomainRuleViolation.class, () -> Product.create("Keyboard", null, 1));
        assertThrows(DomainRuleViolation.class, () -> Product.create("Keyboard", BigDecimal.ONE, null));
        assertThrows(DomainRuleViolation.class, () -> Product.restore(null, "Keyboard", BigDecimal.ONE, 1));
    }

    @Test
    void rejectsNegativePriceAndStockOnCreateAndUpdate() {
        assertThrows(DomainRuleViolation.class, () -> Product.create("Keyboard", new BigDecimal("-0.01"), 1));
        assertThrows(DomainRuleViolation.class, () -> Product.create("Keyboard", BigDecimal.ONE, -1));
        Product product = Product.restore(7L, "Keyboard", BigDecimal.ONE, 1);
        assertThrows(DomainRuleViolation.class, () -> product.changePrice(new BigDecimal("-1")));
        assertThrows(DomainRuleViolation.class, () -> product.changeStock(-1));
        assertEquals(BigDecimal.ONE, product.getPrice());
        assertEquals(1, product.getStock());
    }

    @Test
    void priceChangePreservesIdentityNameStockAndDecimalPrecision() {
        Product product = Product.restore(7L, "Keyboard", new BigDecimal("350000"), 20);
        Product updated = product.changePrice(new BigDecimal("375000.125"));

        assertEquals(7L, updated.getId());
        assertEquals("Keyboard", updated.getName());
        assertEquals(20, updated.getStock());
        assertEquals(new BigDecimal("375000.125"), updated.getPrice());
        assertEquals(new BigDecimal("350000"), product.getPrice());
    }

    @Test
    void replacesAllEditableAttributesWhilePreservingId() {
        Product product = Product.restore(7L, "Keyboard", BigDecimal.ONE, 20);
        Product updated = product.replace("Mouse", new BigDecimal("150000"), 15);

        assertEquals(7L, updated.getId());
        assertEquals("Mouse", updated.getName());
        assertEquals(new BigDecimal("150000"), updated.getPrice());
        assertEquals(15, updated.getStock());
        assertThrows(DomainRuleViolation.class, () -> product.replace("Mouse", null, 15));
    }
}
