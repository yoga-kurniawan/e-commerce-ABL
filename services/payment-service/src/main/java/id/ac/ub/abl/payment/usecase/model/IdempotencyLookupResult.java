package id.ac.ub.abl.payment.usecase.model;

import java.time.Instant;
import java.util.Objects;

/** Inspeksi recovery tidak mengambil alih klaim atau membuktikan rollback. */
public sealed interface IdempotencyLookupResult {
    record Completed(StoredCreationResponse response) implements IdempotencyLookupResult {
        public Completed { Objects.requireNonNull(response, "response"); }
    }
    record Processing(Instant leaseUntil) implements IdempotencyLookupResult {
        public Processing { Objects.requireNonNull(leaseUntil, "leaseUntil"); }
    }
    record Missing() implements IdempotencyLookupResult { }
    record PayloadMismatch() implements IdempotencyLookupResult { }
}
