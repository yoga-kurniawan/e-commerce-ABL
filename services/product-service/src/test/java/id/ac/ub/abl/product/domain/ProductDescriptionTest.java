package id.ac.ub.abl.product.domain;

import java.math.BigDecimal;
import id.ac.ub.abl.product.domain.exception.DomainRuleViolation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductDescriptionTest {
    @Test void updatesAndDeactivationPreserveDescriptionUntilExplicitlyChanged() {
        var product = Product.restore(7L, "Keyboard", BigDecimal.ONE, 20, true, "Keep history");
        var updated = product.replace("Mouse", BigDecimal.TEN, 10).changePrice(BigDecimal.ZERO)
                .changeName("New").changeStock(0).deactivate();
        assertThat(updated.getDescription()).isEqualTo("Keep history");
        assertThat(updated.getId()).isEqualTo(7L);
        assertThat(updated.isActive()).isFalse();
        assertThat(product.changeDescription(null).getDescription()).isNull();
        assertThat(product.changeDescription("").getDescription()).isEmpty();
        assertThat(product.getDescription()).isEqualTo("Keep history");
    }

    @Test void descriptionLimitAppliesToDomainCreationAndUpdates() {
        var product = Product.create("K", BigDecimal.ZERO, 0, "x".repeat(1000));
        assertThat(product.getDescription()).hasSize(1000);
        assertThatThrownBy(() -> product.changeDescription("x".repeat(1001)))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> Product.create("K", BigDecimal.ONE, 1, "x".repeat(1001)))
                .isInstanceOf(DomainRuleViolation.class);
    }
}
