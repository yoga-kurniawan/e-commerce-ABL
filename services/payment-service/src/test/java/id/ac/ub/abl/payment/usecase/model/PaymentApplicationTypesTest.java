package id.ac.ub.abl.payment.usecase.model;

import java.math.BigDecimal;
import java.time.Instant;
import id.ac.ub.abl.payment.domain.OrderSnapshot;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.domain.PaymentMethod;
import id.ac.ub.abl.payment.domain.PaymentStatus;
import id.ac.ub.abl.payment.usecase.command.CreatePaymentCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PaymentApplicationTypesTest {
    private final StoredCreationResponse response = new StoredCreationResponse(201, "application/json",
            "{\"id\":9001, \"status\":\"PENDING\", \"amount\":350000.12500}");

    @Test
    void replay_whenCurrentPaymentChanges_retainsInitialBodyAndMetadata() {
        var pending = Payment.restore(9001L, 501L, PaymentMethod.TRANSFER,
                new BigDecimal("350000.12500"), PaymentStatus.PENDING, null);
        var created = new CreatePaymentResult.Created(pending, response);
        var current = pending.applyResult(PaymentStatus.SUCCESS);
        var replay = new CreatePaymentResult.Replayed(created.response());
        assertThat(current.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(replay.response()).isSameAs(response);
        assertThat(replay.response().body()).isEqualTo("{\"id\":9001, \"status\":\"PENDING\", \"amount\":350000.12500}");
        assertThat(replay.response().statusCode()).isEqualTo(201);
        assertThat(replay.response().contentType()).isEqualTo("application/json");
    }

    @Test
    void storedResponse_whenErrorOrNonJsonOrBlank_rejectsAsReplaySnapshot() {
        assertThatThrownBy(() -> new StoredCreationResponse(409, "application/json", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoredCreationResponse(500, "application/json", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoredCreationResponse(201, "text/plain", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoredCreationResponse(201, "application/json", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoredCreationResponse(201, "application/json", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createdResult_whenNotPersistedOrNotPending_rejects() {
        var unsaved = Payment.create(new OrderSnapshot(501L, BigDecimal.ONE, OrderSnapshot.Status.PENDING), PaymentMethod.COD);
        assertThatThrownBy(() -> new CreatePaymentResult.Created(unsaved, response))
                .isInstanceOf(IllegalArgumentException.class);
        var terminal = Payment.restore(1L, 501L, PaymentMethod.COD, BigDecimal.ONE, PaymentStatus.FAILED, null);
        assertThatThrownBy(() -> new CreatePaymentResult.Created(terminal, response))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreatePaymentResult.Created(null, response)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreatePaymentResult.Replayed(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void claim_whenRehydratingExpiredLease_preservesTokenWithoutUsingHostClock() {
        var command = new CreatePaymentCommand(501L, PaymentMethod.COD, new IdempotencyKey("A"));
        var lease = Instant.parse("2026-01-01T00:00:00Z");
        var claim = new IdempotencyClaim(command, "owner-1", lease);
        assertThat(claim.request()).isSameAs(command);
        assertThat(claim.ownerToken()).isEqualTo("owner-1");
        assertThat(claim.leaseUntil()).isEqualTo(lease);
        assertThatThrownBy(() -> new IdempotencyClaim(command, " ", lease)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyClaim(command, null, lease)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyClaim(null, "owner", lease)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IdempotencyClaim(command, "owner", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void outcomeVariants_whenRequiredDataMissing_rejectAmbiguousState() {
        assertThatThrownBy(() -> new IdempotencyClaimResult.Claimed(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IdempotencyClaimResult.Replayed(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IdempotencyLookupResult.Completed(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IdempotencyLookupResult.Processing(null)).isInstanceOf(NullPointerException.class);
    }
}
