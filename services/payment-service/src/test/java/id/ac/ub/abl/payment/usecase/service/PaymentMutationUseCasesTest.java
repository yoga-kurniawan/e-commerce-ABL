package id.ac.ub.abl.payment.usecase.service;

import java.math.BigDecimal;
import java.util.Optional;
import id.ac.ub.abl.payment.domain.*;
import id.ac.ub.abl.payment.domain.exception.*;
import id.ac.ub.abl.payment.usecase.command.*;
import id.ac.ub.abl.payment.usecase.exception.*;
import id.ac.ub.abl.payment.usecase.port.out.PaymentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Fake port memakai aturan domain nyata; bukan bukti transaksi PostgreSQL. */
class PaymentMutationUseCasesTest {
    final StatefulStore store = new StatefulStore();
    final GetPaymentUseCase get = new GetPaymentUseCase(store);
    final PatchPaymentUseCase patch = new PatchPaymentUseCase(store);
    final ApplyPaymentResultUseCase result = new ApplyPaymentResultUseCase(store);
    @Test void get_whenFoundOrMissing_shouldReadWithoutMutation() {
        assertThat(get.execute(1L)).isSameAs(store.payment);
        assertThatThrownBy(() -> get.execute(99L)).isInstanceOfSatisfying(PaymentOperationException.class,
                error -> assertThat(error.getCode()).isEqualTo("NOT_FOUND"));
        assertThatThrownBy(() -> get.execute(null)).isInstanceOf(InvalidPaymentCommandException.class);
        assertThat(store.effects).isZero();
    }
    @Test void patch_whenPending_shouldKeepAmountMethodAndStatusAndExactReference() {
        var changed = patch.execute(new PatchPaymentCommand(1L, "  bukti pembayaran  "));
        assertThat(changed.getTransactionReference()).isEqualTo("  bukti pembayaran  ");
        assertThat(changed.getAmount()).isEqualTo(new BigDecimal("350000.12500"));
        assertThat(changed.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(changed.getMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(store.effects).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings = {"SUCCESS", "FAILED"})
    void apply_whenFinalResultRepeated_shouldApplyOneEffectAndRejectOppositeAndPatch(String status) {
        var command = new ApplyPaymentResultCommand(1L, PaymentStatus.valueOf(status));
        var first = result.execute(command);
        assertThat(result.execute(command)).isSameAs(first);
        assertThat(store.effects).isEqualTo(1);
        assertThatThrownBy(() -> patch.execute(new PatchPaymentCommand(1L, "ref"))).isInstanceOf(PaymentNotModifiableException.class);
        var opposite = first.getStatus() == PaymentStatus.SUCCESS ? PaymentStatus.FAILED : PaymentStatus.SUCCESS;
        assertThatThrownBy(() -> result.execute(new ApplyPaymentResultCommand(1L, opposite)))
                .isInstanceOf(PaymentResultConflictException.class);
        assertThat(store.effects).isEqualTo(1);
        assertThat(get.execute(1L)).isSameAs(first);
    }
    @Test void mutation_whenResourceMissing_shouldPropagateNotFoundAndApplyNoEffect() {
        assertThatThrownBy(() -> patch.execute(new PatchPaymentCommand(99L, "ref")))
                .isInstanceOfSatisfying(PaymentOperationException.class, error -> assertThat(error.getCode()).isEqualTo("NOT_FOUND"));
        assertThatThrownBy(() -> result.execute(new ApplyPaymentResultCommand(99L, PaymentStatus.SUCCESS)))
                .isInstanceOfSatisfying(PaymentOperationException.class, error -> assertThat(error.getCode()).isEqualTo("NOT_FOUND"));
        assertThat(store.effects).isZero();
    }
    static class StatefulStore implements PaymentStore {
        Payment payment = Payment.restore(1L, 501L, PaymentMethod.COD, new BigDecimal("350000.12500"), PaymentStatus.PENDING, null);
        int effects;
        public Optional<Payment> findById(Long id) { return id.equals(payment.getId()) ? Optional.of(payment) : Optional.empty(); }
        Payment required(Long id) { return findById(id).orElseThrow(() -> new PaymentOperationException("NOT_FOUND", "missing")); }
        public Payment changeReference(PatchPaymentCommand command) {
            payment = required(command.paymentId()).changeReference(command.transactionReference()); effects++; return payment;
        }
        public Payment applyResult(ApplyPaymentResultCommand command) {
            Payment current = required(command.paymentId());
            Payment next = current.applyResult(command.result());
            if (current != next) { effects++; payment = next; }
            return payment;
        }
    }
}
