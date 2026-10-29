package id.ac.ub.abl.payment.usecase.service;

import java.util.Objects;
import id.ac.ub.abl.payment.domain.OrderSnapshot;
import id.ac.ub.abl.payment.usecase.command.CreatePaymentCommand;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import id.ac.ub.abl.payment.usecase.exception.PaymentCreationRolledBackException;
import id.ac.ub.abl.payment.usecase.model.*;
import id.ac.ub.abl.payment.usecase.port.out.IdempotencyStore;
import id.ac.ub.abl.payment.usecase.port.out.OrderLookup;
import id.ac.ub.abl.payment.usecase.port.out.PaymentCreationCommitter;
import org.springframework.stereotype.Service;

/** Tidak membuka transaksi SQL: lookup HTTP berada di antara dua transaksi adapter terpisah. */
@Service
public class CreatePaymentUseCase {
    private final IdempotencyStore idempotency;
    private final OrderLookup orders;
    private final PaymentCreationCommitter committer;
    public CreatePaymentUseCase(IdempotencyStore idempotency, OrderLookup orders, PaymentCreationCommitter committer) {
        this.idempotency = idempotency; this.orders = orders; this.committer = committer;
    }
    public CreatePaymentResult execute(CreatePaymentCommand command) {
        Objects.requireNonNull(command, "command");
        var outcome = idempotency.claim(command);
        if (outcome instanceof IdempotencyClaimResult.Replayed replay) { return new CreatePaymentResult.Replayed(replay.response()); }
        if (outcome instanceof IdempotencyClaimResult.PayloadMismatch) { throw error("IDEMPOTENCY_KEY_REUSED"); }
        if (outcome instanceof IdempotencyClaimResult.InProgress) { throw error("REQUEST_IN_PROGRESS"); }
        var claim = ((IdempotencyClaimResult.Claimed) outcome).claim();
        OrderSnapshot order;
        try {
            order = orders.findById(command.orderId()).orElseThrow(() -> error("ORDER_NOT_FOUND"));
            if (!command.orderId().equals(order.id())) { throw error("ORDER_UPSTREAM_ERROR"); }
            order.requirePayable();
        } catch (RuntimeException beforeCommit) {
            release(claim, beforeCommit);
            throw beforeCommit;
        }
        try {
            return committer.commit(claim, order);
        } catch (PaymentCreationRolledBackException rolledBack) {
            var failure = rolledBack.failure();
            release(claim, failure);
            throw failure;
        } catch (RuntimeException uncertain) {
            return reconcile(command, uncertain);
        }
    }
    private CreatePaymentResult reconcile(CreatePaymentCommand command, RuntimeException uncertain) {
        IdempotencyLookupResult state;
        try { state = idempotency.inspect(command); }
        catch (RuntimeException unavailable) {
            if (uncertain != unavailable) { uncertain.addSuppressed(unavailable); }
            throw new PaymentOperationException("DEPENDENCY_UNAVAILABLE", "Hasil create belum dapat dipastikan; retry dengan key sama", uncertain);
        }
        if (state instanceof IdempotencyLookupResult.Completed completed) { return new CreatePaymentResult.Replayed(completed.response()); }
        if (state instanceof IdempotencyLookupResult.PayloadMismatch) {
            throw new PaymentOperationException("IDEMPOTENCY_KEY_REUSED", "Key terikat pada payload berbeda", uncertain);
        }
        throw new PaymentOperationException("DEPENDENCY_UNAVAILABLE", "Hasil create belum dapat dipastikan; retry dengan key sama", uncertain);
    }
    private void release(IdempotencyClaim claim, RuntimeException primary) {
        try { idempotency.releaseAfterConfirmedRollback(claim); }
        catch (RuntimeException cleanupFailure) { if (cleanupFailure != primary) { primary.addSuppressed(cleanupFailure); } }
    }
    private PaymentOperationException error(String code) { return new PaymentOperationException(code, "Create Payment ditolak: " + code); }
}
