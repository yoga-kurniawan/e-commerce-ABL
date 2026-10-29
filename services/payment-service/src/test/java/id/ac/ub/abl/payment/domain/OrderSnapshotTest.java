package id.ac.ub.abl.payment.domain;

import java.math.BigDecimal;

import id.ac.ub.abl.payment.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.payment.domain.exception.OrderNotPayableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

class OrderSnapshotTest {
    @Test
    void constructor_whenValid_preservesTotalWithoutRounding() {
        var total = new BigDecimal("350000.12500");
        var snapshot = new OrderSnapshot(501L, total, OrderSnapshot.Status.PENDING);
        assertThat(snapshot.id()).isEqualTo(501L);
        assertThat(snapshot.total()).isEqualTo(total);
        assertThatCode(snapshot::requirePayable).doesNotThrowAnyException();
    }

    @Test
    void constructor_whenRequiredDataMissingOrTotalNegative_rejectsSnapshot() {
        assertThatThrownBy(() -> new OrderSnapshot(null, BigDecimal.ONE, OrderSnapshot.Status.PENDING))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> new OrderSnapshot(1L, null, OrderSnapshot.Status.PENDING))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> new OrderSnapshot(1L, new BigDecimal("-0.001"), OrderSnapshot.Status.PENDING))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> new OrderSnapshot(1L, BigDecimal.ONE, null))
                .isInstanceOf(DomainRuleViolation.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderSnapshot.Status.class, names = {"CONFIRMED", "CANCELLED"})
    void requirePayable_whenOrderNotPending_rejectsWithoutChangingSnapshot(OrderSnapshot.Status status) {
        var snapshot = new OrderSnapshot(501L, BigDecimal.TEN, status);
        assertThatThrownBy(snapshot::requirePayable).isInstanceOf(OrderNotPayableException.class);
        assertThat(snapshot.status()).isEqualTo(status);
    }

    @Test
    void requirePayable_whenTotalZero_allowsOrder() {
        assertThatCode(new OrderSnapshot(501L, BigDecimal.ZERO, OrderSnapshot.Status.PENDING)::requirePayable)
                .doesNotThrowAnyException();
    }
}
