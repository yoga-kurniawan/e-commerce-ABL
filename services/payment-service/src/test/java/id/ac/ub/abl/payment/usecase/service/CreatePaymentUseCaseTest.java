package id.ac.ub.abl.payment.usecase.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import id.ac.ub.abl.payment.domain.*;
import id.ac.ub.abl.payment.domain.exception.OrderNotPayableException;
import id.ac.ub.abl.payment.usecase.command.CreatePaymentCommand;
import id.ac.ub.abl.payment.usecase.exception.*;
import id.ac.ub.abl.payment.usecase.model.*;
import id.ac.ub.abl.payment.usecase.port.out.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CreatePaymentUseCaseTest {
    @Mock IdempotencyStore keys;
    @Mock OrderLookup orders;
    @Mock PaymentCreationCommitter committer;
    @InjectMocks CreatePaymentUseCase useCase;
    final CreatePaymentCommand command = new CreatePaymentCommand(501L, PaymentMethod.COD, new IdempotencyKey("key"));
    final IdempotencyClaim claim = new IdempotencyClaim(command, "owner", Instant.parse("2026-10-29T12:00:30Z"));
    final OrderSnapshot order = new OrderSnapshot(501L, new BigDecimal("350000.12500"), OrderSnapshot.Status.PENDING);
    final StoredCreationResponse response = new StoredCreationResponse(201, "application/json", "{\"status\":\"PENDING\"}");
    void acquired() { when(keys.claim(command)).thenReturn(new IdempotencyClaimResult.Claimed(claim)); }
    void ready() { acquired(); when(orders.findById(501L)).thenReturn(Optional.of(order)); }
    @Test void create_whenValid_shouldClaimLookupThenCommitExactSnapshot() {
        ready();
        var created = new CreatePaymentResult.Created(Payment.restore(1L, 501L, PaymentMethod.COD,
                order.total(), PaymentStatus.PENDING, null), response);
        when(committer.commit(claim, order)).thenReturn(created);
        assertThat(useCase.execute(command)).isSameAs(created);
        var sequence = inOrder(keys, orders, committer);
        sequence.verify(keys).claim(command); sequence.verify(orders).findById(501L); sequence.verify(committer).commit(claim, order);
        verifyNoMoreInteractions(keys, orders, committer);
    }
    @Test void replay_whenOrderWouldBeUnavailable_shouldReturnInitialBodyWithoutLookup() {
        when(keys.claim(command)).thenReturn(new IdempotencyClaimResult.Replayed(response));
        assertThat(useCase.execute(command)).isEqualTo(new CreatePaymentResult.Replayed(response));
        verifyNoInteractions(orders, committer);
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void claim_whenNotAcquired_shouldFailWithoutLookupOrRelease(boolean mismatch) {
        when(keys.claim(command)).thenReturn(mismatch ? new IdempotencyClaimResult.PayloadMismatch() : new IdempotencyClaimResult.InProgress());
        assertCode(() -> useCase.execute(command), mismatch ? "IDEMPOTENCY_KEY_REUSED" : "REQUEST_IN_PROGRESS");
        verifyNoInteractions(orders, committer); verify(keys, never()).releaseAfterConfirmedRollback(any());
    }
    @Test void order_whenMissing_shouldReleaseClaimAndReportOrderNotFound() {
        acquired(); when(orders.findById(501L)).thenReturn(Optional.empty());
        assertCode(() -> useCase.execute(command), "ORDER_NOT_FOUND");
        verify(keys).releaseAfterConfirmedRollback(claim); verifyNoInteractions(committer);
    }
    @ParameterizedTest @ValueSource(strings = {"CONFIRMED", "CANCELLED"})
    void order_whenNotPayable_shouldReleaseWithoutCommitting(String status) {
        acquired(); when(orders.findById(501L)).thenReturn(Optional.of(new OrderSnapshot(501L, BigDecimal.ONE, OrderSnapshot.Status.valueOf(status))));
        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(OrderNotPayableException.class);
        verify(keys).releaseAfterConfirmedRollback(claim); verifyNoInteractions(committer);
    }
    @ParameterizedTest @ValueSource(strings = {"ORDER_TIMEOUT", "DEPENDENCY_UNAVAILABLE", "ORDER_UPSTREAM_ERROR"})
    void lookup_whenDependencyFails_shouldReleaseBecauseFinalizationNeverStarted(String code) {
        acquired(); var failure = new PaymentOperationException(code, "lookup fault");
        when(orders.findById(501L)).thenThrow(failure);
        assertThatThrownBy(() -> useCase.execute(command)).isSameAs(failure);
        verify(keys).releaseAfterConfirmedRollback(claim); verifyNoInteractions(committer);
    }
    @Test void lookup_whenWrongId_shouldRejectAndRelease() {
        acquired(); when(orders.findById(501L)).thenReturn(Optional.of(new OrderSnapshot(502L, BigDecimal.ONE, OrderSnapshot.Status.PENDING)));
        assertCode(() -> useCase.execute(command), "ORDER_UPSTREAM_ERROR"); verify(keys).releaseAfterConfirmedRollback(claim);
        verifyNoInteractions(committer);
    }
    @ParameterizedTest @ValueSource(strings = {"PAYMENT_ALREADY_PENDING", "ORDER_ALREADY_PAID", "REQUEST_IN_PROGRESS", "DEPENDENCY_UNAVAILABLE"})
    void finalize_whenRollbackConfirmed_shouldReleaseAndPreserveOriginalFailure(String code) {
        ready(); var failure = new PaymentOperationException(code, "finalization fault");
        when(committer.commit(claim, order)).thenThrow(new PaymentCreationRolledBackException(failure));
        assertThatThrownBy(() -> useCase.execute(command)).isSameAs(failure);
        verify(keys).releaseAfterConfirmedRollback(claim); verify(keys, never()).inspect(any());
    }
    @Test void release_whenItFails_shouldKeepPrimaryFailureAndLeaveRecoveryToRetry() {
        acquired(); var original = new PaymentOperationException("ORDER_TIMEOUT", "timeout");
        when(orders.findById(501L)).thenThrow(original);
        var releaseFailure = new PaymentOperationException("DEPENDENCY_UNAVAILABLE", "release failure");
        when(keys.releaseAfterConfirmedRollback(claim)).thenThrow(releaseFailure);
        assertThatThrownBy(() -> useCase.execute(command)).isSameAs(original).hasSuppressedException(releaseFailure);
    }
    @Test void finalize_whenCommitAcknowledgementLost_shouldInspectAndReplayWithoutReleasing() {
        ready(); when(committer.commit(claim, order)).thenThrow(new PaymentOperationException("DEPENDENCY_UNAVAILABLE", "unknown"));
        when(keys.inspect(command)).thenReturn(new IdempotencyLookupResult.Completed(response));
        assertThat(useCase.execute(command)).isEqualTo(new CreatePaymentResult.Replayed(response));
        verify(keys, never()).releaseAfterConfirmedRollback(any()); verify(committer).commit(claim, order);
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void inspect_whenMissingOrProcessing_shouldNeverAssumeRollback(boolean processing) {
        ready(); when(committer.commit(claim, order)).thenThrow(new IllegalStateException("unclassified failure"));
        when(keys.inspect(command)).thenReturn(processing ? new IdempotencyLookupResult.Processing(claim.leaseUntil()) : new IdempotencyLookupResult.Missing());
        assertCode(() -> useCase.execute(command), "DEPENDENCY_UNAVAILABLE"); verify(keys, never()).releaseAfterConfirmedRollback(any());
    }
    @Test void inspect_whenMismatch_shouldReportReusedKeyWithoutRelease() {
        ready(); when(committer.commit(claim, order)).thenThrow(new IllegalStateException("unknown"));
        when(keys.inspect(command)).thenReturn(new IdempotencyLookupResult.PayloadMismatch());
        assertCode(() -> useCase.execute(command), "IDEMPOTENCY_KEY_REUSED"); verify(keys, never()).releaseAfterConfirmedRollback(any());
    }
    @Test void inspect_whenUnavailable_shouldPreserveClaimAndReturnDependencyFailure() {
        ready(); when(committer.commit(claim, order)).thenThrow(new IllegalStateException("unknown"));
        when(keys.inspect(command)).thenThrow(new PaymentOperationException("DEPENDENCY_UNAVAILABLE", "offline"));
        assertCode(() -> useCase.execute(command), "DEPENDENCY_UNAVAILABLE"); verify(keys, never()).releaseAfterConfirmedRollback(any());
    }
    @Test void claim_whenAcknowledgementLost_shouldNotLookupOrReleaseWithoutOwner() {
        when(keys.claim(command)).thenThrow(new PaymentOperationException("DEPENDENCY_UNAVAILABLE", "claim unknown"));
        assertCode(() -> useCase.execute(command), "DEPENDENCY_UNAVAILABLE"); verifyNoInteractions(orders, committer);
        verify(keys, never()).releaseAfterConfirmedRollback(any());
    }
    static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(PaymentOperationException.class, error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
