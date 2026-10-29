package id.ac.ub.abl.payment.usecase.model;

import java.util.Objects;

/** Hasil klaim atomik: acquired, replay, konflik sementara, atau mismatch payload. */
public sealed interface IdempotencyClaimResult {
    record Claimed(IdempotencyClaim claim) implements IdempotencyClaimResult {
        public Claimed { Objects.requireNonNull(claim, "claim"); }
    }
    record Replayed(StoredCreationResponse response) implements IdempotencyClaimResult {
        public Replayed { Objects.requireNonNull(response, "response"); }
    }
    record InProgress() implements IdempotencyClaimResult { }
    record PayloadMismatch() implements IdempotencyClaimResult { }
}
