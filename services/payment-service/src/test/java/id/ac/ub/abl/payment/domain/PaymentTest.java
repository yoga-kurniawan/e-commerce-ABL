package id.ac.ub.abl.payment.domain;

import java.math.BigDecimal;

import id.ac.ub.abl.payment.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.payment.domain.exception.OrderNotPayableException;
import id.ac.ub.abl.payment.domain.exception.PaymentNotModifiableException;
import id.ac.ub.abl.payment.domain.exception.PaymentResultConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class PaymentTest {
    private static final BigDecimal TOTAL = new BigDecimal("350000.12500");

    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    void create_whenOrderPayable_startsPendingAndCopiesExactTotal(PaymentMethod method) {
        var payment = Payment.create(new OrderSnapshot(501L, TOTAL, OrderSnapshot.Status.PENDING), method);
        assertThat(payment.getId()).isNull();
        assertThat(payment.getOrderId()).isEqualTo(501L);
        assertThat(payment.getMethod()).isEqualTo(method);
        assertThat(payment.getAmount()).isEqualTo(TOTAL);
        assertThat(payment.getAmount().scale()).isEqualTo(5);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getTransactionReference()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = OrderSnapshot.Status.class, names = {"CONFIRMED", "CANCELLED"})
    void create_whenOrderNotPayable_rejectsPayment(OrderSnapshot.Status status) {
        assertThatThrownBy(() -> Payment.create(new OrderSnapshot(501L, TOTAL, status), PaymentMethod.TRANSFER))
                .isInstanceOf(OrderNotPayableException.class);
    }

    @Test
    void create_whenDataMissing_rejectsPayment() {
        assertThatThrownBy(() -> Payment.create(null, PaymentMethod.COD)).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> Payment.create(new OrderSnapshot(501L, TOTAL, OrderSnapshot.Status.PENDING), null))
                .isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void create_whenTotalZero_preservesZeroAndPending() {
        var payment = Payment.create(new OrderSnapshot(501L, BigDecimal.ZERO, OrderSnapshot.Status.PENDING), PaymentMethod.COD);
        assertThat(payment.getAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void changeReference_whenPending_preservesTextAndOtherFieldsWithoutMutatingOriginal() {
        var original = fixture(PaymentStatus.PENDING);
        var updated = original.changeReference("  TRX-001  ").changeReference("TRX-002");
        assertThat(original.getTransactionReference()).isNull();
        assertThat(original.changeReference("  TRX-001  ").getTransactionReference()).isEqualTo("  TRX-001  ");
        assertThat(updated.getTransactionReference()).isEqualTo("TRX-002");
        assertThat(updated.getId()).isEqualTo(original.getId());
        assertThat(updated.getOrderId()).isEqualTo(original.getOrderId());
        assertThat(updated.getMethod()).isEqualTo(original.getMethod());
        assertThat(updated.getAmount()).isEqualTo(TOTAL);
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u00a0", "\u2003", "\u202f"})
    void changeReference_whenNullOrBlank_rejectsWithoutMutation(String reference) {
        var payment = fixture(PaymentStatus.PENDING);
        assertThatThrownBy(() -> payment.changeReference(reference)).isInstanceOf(DomainRuleViolation.class);
        assertThat(payment.getTransactionReference()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "FAILED"})
    void changeReference_whenTerminal_rejects(PaymentStatus status) {
        var payment = fixture(status);
        assertThatThrownBy(() -> payment.changeReference("TRX-001")).isInstanceOf(PaymentNotModifiableException.class);
        assertThat(payment.getStatus()).isEqualTo(status);
        assertThat(payment.getTransactionReference()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "FAILED"})
    void applyResult_whenPending_movesToTerminalAndPreservesPaymentData(PaymentStatus result) {
        var original = fixture(PaymentStatus.PENDING).changeReference("TRX-001");
        var updated = original.applyResult(result);
        assertThat(original.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(updated.getStatus()).isEqualTo(result);
        assertThat(updated.getId()).isEqualTo(original.getId());
        assertThat(updated.getOrderId()).isEqualTo(original.getOrderId());
        assertThat(updated.getAmount()).isEqualTo(TOTAL);
        assertThat(updated.getMethod()).isEqualTo(original.getMethod());
        assertThat(updated.getTransactionReference()).isEqualTo("TRX-001");
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "FAILED"})
    void applyResult_whenTerminalResultRepeated_returnsSameInstance(PaymentStatus result) {
        var payment = fixture(result);
        assertThat(payment.applyResult(result)).isSameAs(payment);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "FAILED"})
    void applyResult_whenTerminalResultContradicted_rejectsWithoutMutation(PaymentStatus status) {
        var payment = fixture(status);
        var opposite = status == PaymentStatus.SUCCESS ? PaymentStatus.FAILED : PaymentStatus.SUCCESS;
        assertThatThrownBy(() -> payment.applyResult(opposite)).isInstanceOf(PaymentResultConflictException.class);
        assertThat(payment.getStatus()).isEqualTo(status);
    }

    @Test
    void applyResult_whenNullOrPending_rejectsWithoutMutation() {
        var payment = fixture(PaymentStatus.PENDING);
        assertThatThrownBy(() -> payment.applyResult(null)).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> payment.applyResult(PaymentStatus.PENDING)).isInstanceOf(DomainRuleViolation.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void restore_whenRequiredStateInvalid_rejects() {
        assertThatThrownBy(() -> Payment.restore(null, 501L, PaymentMethod.COD, TOTAL, PaymentStatus.PENDING, null))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> Payment.restore(1L, null, PaymentMethod.COD, TOTAL, PaymentStatus.PENDING, null))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> Payment.restore(1L, 501L, null, TOTAL, PaymentStatus.PENDING, null))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> Payment.restore(1L, 501L, PaymentMethod.COD, null, PaymentStatus.PENDING, null))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> Payment.restore(1L, 501L, PaymentMethod.COD, new BigDecimal("-0.001"), PaymentStatus.PENDING, null))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> Payment.restore(1L, 501L, PaymentMethod.COD, TOTAL, null, null))
                .isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> Payment.restore(1L, 501L, PaymentMethod.COD, TOTAL, PaymentStatus.PENDING, " "))
                .isInstanceOf(DomainRuleViolation.class);
    }

    private static Payment fixture(PaymentStatus status) {
        return Payment.restore(9001L, 501L, PaymentMethod.TRANSFER, TOTAL, status, null);
    }
}
