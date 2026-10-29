package id.ac.ub.abl.payment.usecase.command;

import id.ac.ub.abl.payment.domain.PaymentMethod;
import id.ac.ub.abl.payment.domain.PaymentStatus;
import id.ac.ub.abl.payment.usecase.exception.InvalidPaymentCommandException;
import id.ac.ub.abl.payment.usecase.model.IdempotencyKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class PaymentCommandsTest {
    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    void create_whenValid_preservesInputAndCanonicalizesValues(PaymentMethod method) {
        var command = new CreatePaymentCommand(501L, method, new IdempotencyKey("Key-001"));
        assertThat(command.orderId()).isEqualTo(501L);
        assertThat(command.method()).isEqualTo(method);
        assertThat(command.key().value()).isEqualTo("Key-001");
        assertThat(command.canonicalPayload()).isEqualTo("v1\norderId=501\nmethod=" + method);
    }

    @Test
    void create_whenMissingInput_rejectsBeforeAnOperationCanBeClaimed() {
        var key = new IdempotencyKey("A");
        assertThatThrownBy(() -> new CreatePaymentCommand(null, PaymentMethod.COD, key))
                .isInstanceOf(InvalidPaymentCommandException.class);
        assertThatThrownBy(() -> new CreatePaymentCommand(1L, null, key))
                .isInstanceOf(InvalidPaymentCommandException.class);
        assertThatThrownBy(() -> new CreatePaymentCommand(1L, PaymentMethod.COD, null))
                .isInstanceOf(InvalidPaymentCommandException.class);
    }

    @Test
    void fingerprint_whenPayloadEqual_usesSpecifiedUtf8Sha256RegardlessOfKey() {
        var a = new CreatePaymentCommand(501L, PaymentMethod.TRANSFER, new IdempotencyKey("A"));
        var b = new CreatePaymentCommand(501L, PaymentMethod.TRANSFER, new IdempotencyKey("B"));
        assertThat(a.fingerprint()).isEqualTo("027c2bb27267bf00e7e4c0e366d061ff04d144606d7f654a4e9b9f27f1f19af2");
        assertThat(b.fingerprint()).isEqualTo(a.fingerprint());
        assertThat(a.hasSamePayload(b)).isTrue();
        assertThat(a.hasSamePayload(null)).isFalse();
        var otherMethod = new CreatePaymentCommand(501L, PaymentMethod.COD, a.key());
        var otherOrder = new CreatePaymentCommand(502L, PaymentMethod.TRANSFER, a.key());
        assertThat(a.hasSamePayload(otherMethod)).isFalse();
        assertThat(a.hasSamePayload(otherOrder)).isFalse();
        assertThat(a.fingerprint()).isNotEqualTo(otherMethod.fingerprint()).isNotEqualTo(otherOrder.fingerprint());
    }

    @Test
    void create_whenLongBoundsUsed_doesNotIntroduceUnapprovedPositiveIdConstraint() {
        var key = new IdempotencyKey("A");
        assertThat(new CreatePaymentCommand(Long.MIN_VALUE, PaymentMethod.COD, key).canonicalPayload())
                .contains("orderId=-9223372036854775808");
        assertThat(new CreatePaymentCommand(Long.MAX_VALUE, PaymentMethod.COD, key).canonicalPayload())
                .contains("orderId=9223372036854775807");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " A", "A ", "A\n", "_A", "A/B", "é", "A!"})
    void key_whenOutsideContract_rejectsWithoutTrimming(String value) {
        assertThatThrownBy(() -> new IdempotencyKey(value)).isInstanceOf(InvalidPaymentCommandException.class);
    }

    @Test
    void key_whenAtLengthBoundaryAndCaseDiffers_preservesIdentity() {
        assertThat(new IdempotencyKey("A").value()).isEqualTo("A");
        assertThat(new IdempotencyKey("A".repeat(128)).value()).hasSize(128);
        assertThatThrownBy(() -> new IdempotencyKey("A".repeat(129))).isInstanceOf(InvalidPaymentCommandException.class);
        assertThat(new IdempotencyKey("Key._:-001")).isNotEqualTo(new IdempotencyKey("key._:-001"));
        assertThat(IdempotencyKey.OPERATION).isEqualTo("CREATE_PAYMENT_V1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t", "\u00a0", "\u2003"})
    void patch_whenReferenceInvalid_rejects(String reference) {
        assertThatThrownBy(() -> new PatchPaymentCommand(1L, reference)).isInstanceOf(InvalidPaymentCommandException.class);
    }

    @Test
    void patch_whenValid_preservesReferenceAndRequiresResourceId() {
        var command = new PatchPaymentCommand(1L, "  TRX-001  ");
        assertThat(command.paymentId()).isEqualTo(1L);
        assertThat(command.transactionReference()).isEqualTo("  TRX-001  ");
        assertThatThrownBy(() -> new PatchPaymentCommand(null, "TRX"))
                .isInstanceOf(InvalidPaymentCommandException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "FAILED"})
    void result_whenFinal_acceptsWithoutImplyingPublicStatusPatch(PaymentStatus status) {
        assertThat(new ApplyPaymentResultCommand(1L, status).result()).isEqualTo(status);
    }

    @Test
    void result_whenNotFinalOrIdMissing_rejects() {
        assertThatThrownBy(() -> new ApplyPaymentResultCommand(1L, PaymentStatus.PENDING))
                .isInstanceOf(InvalidPaymentCommandException.class);
        assertThatThrownBy(() -> new ApplyPaymentResultCommand(1L, null))
                .isInstanceOf(InvalidPaymentCommandException.class);
        assertThatThrownBy(() -> new ApplyPaymentResultCommand(null, PaymentStatus.SUCCESS))
                .isInstanceOf(InvalidPaymentCommandException.class);
    }
}
