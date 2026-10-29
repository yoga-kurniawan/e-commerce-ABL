package id.ac.ub.abl.payment.adapter.outbound.database;

import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.EntityManager;
import id.ac.ub.abl.payment.adapter.outbound.database.entity.*;
import id.ac.ub.abl.payment.usecase.command.CreatePaymentCommand;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import id.ac.ub.abl.payment.usecase.model.*;
import id.ac.ub.abl.payment.usecase.port.out.IdempotencyStore;
import org.springframework.stereotype.Component;

@Component
public class PaymentIdempotencyAdapter implements IdempotencyStore {
    private final PaymentIdempotencyRepository repository;
    private final PaymentDatabaseSession session;
    private final PaymentTransactions transactions;
    private final EntityManager entityManager;
    public PaymentIdempotencyAdapter(PaymentIdempotencyRepository repository, PaymentDatabaseSession session,
            PaymentTransactions transactions, EntityManager entityManager) {
        this.repository = repository; this.session = session; this.transactions = transactions; this.entityManager = entityManager;
    }
    public IdempotencyClaimResult claim(CreatePaymentCommand request) {
        try { return transactions.run("REQUEST_IN_PROGRESS", () -> claimLocked(request)); }
        catch (PaymentOperationException failure) {
            if ("REQUEST_IN_PROGRESS".equals(failure.getCode())) { return new IdempotencyClaimResult.InProgress(); }
            throw failure;
        }
    }
    private IdempotencyClaimResult claimLocked(CreatePaymentCommand request) {
        String token = UUID.randomUUID().toString();
        int inserted = insert(request, token);
        var row = repository.findByIdForUpdate(id(request)).orElse(null);
        if (row == null) { return new IdempotencyClaimResult.InProgress(); } // cleanup race: retry
        Instant now = session.now();
        if (row.getState() == IdempotencyState.COMPLETED && !row.getExpiresAt().isAfter(now)) {
            if (repository.deleteExpiredCompletedByKey(IdempotencyKey.OPERATION, request.key().value()) != 1) {
                return new IdempotencyClaimResult.InProgress();
            }
            entityManager.detach(row);
            inserted = insert(request, token);
            row = repository.findByIdForUpdate(id(request)).orElseThrow();
        }
        if (!matches(row, request)) { return new IdempotencyClaimResult.PayloadMismatch(); }
        if (row.getState() == IdempotencyState.COMPLETED) { return new IdempotencyClaimResult.Replayed(response(row)); }
        if (inserted == 0) {
            now = session.now();
            if (row.getLeaseUntil().isAfter(now)) { return new IdempotencyClaimResult.InProgress(); }
            row.updateOwner(token, now.plus(transactions.lease()));
        }
        return new IdempotencyClaimResult.Claimed(new IdempotencyClaim(request, row.getOwnerToken(), row.getLeaseUntil()));
    }
    public IdempotencyLookupResult inspect(CreatePaymentCommand request) {
        return transactions.run("DEPENDENCY_UNAVAILABLE", () -> {
            var row = repository.findById(id(request)).orElse(null);
            if (row == null) { return new IdempotencyLookupResult.Missing(); }
            if (!matches(row, request)) { return new IdempotencyLookupResult.PayloadMismatch(); }
            if (row.getState() == IdempotencyState.COMPLETED) {
                return row.getExpiresAt().isAfter(session.now()) ? new IdempotencyLookupResult.Completed(response(row))
                        : new IdempotencyLookupResult.Missing();
            }
            return new IdempotencyLookupResult.Processing(row.getLeaseUntil());
        });
    }
    public boolean releaseAfterConfirmedRollback(IdempotencyClaim claim) {
        return transactions.run("REQUEST_IN_PROGRESS", () -> repository.deleteProcessingOwnedBy(
                IdempotencyKey.OPERATION, claim.request().key().value(), claim.ownerToken()) == 1);
    }
    public int deleteExpiredCompleted(int batchSize) {
        if (batchSize < 1) { throw new IllegalArgumentException("Batch harus positif"); }
        return transactions.run("PAYMENT_OPERATION_BUSY", () -> repository.deleteExpiredCompletedBatch(batchSize));
    }
    private int insert(CreatePaymentCommand request, String token) {
        return repository.insertProcessingIfAbsent(IdempotencyKey.OPERATION, request.key().value(), request.orderId(),
                request.method().name(), request.fingerprint(), token, transactions.lease().toMillis());
    }
    static PaymentIdempotencyId id(CreatePaymentCommand request) {
        return new PaymentIdempotencyId(IdempotencyKey.OPERATION, request.key().value());
    }
    static boolean matches(PaymentIdempotencyJpaEntity row, CreatePaymentCommand request) {
        return row.getOrderId().equals(request.orderId()) && row.getMethod() == request.method()
                && row.getFingerprint().equals(request.fingerprint());
    }
    static StoredCreationResponse response(PaymentIdempotencyJpaEntity row) {
        return new StoredCreationResponse(row.getResponseStatus(), row.getResponseContentType(), row.getResponseBody());
    }
}
