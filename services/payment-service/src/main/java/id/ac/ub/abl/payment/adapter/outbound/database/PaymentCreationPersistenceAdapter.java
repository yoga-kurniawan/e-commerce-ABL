package id.ac.ub.abl.payment.adapter.outbound.database;

import java.time.Duration;
import id.ac.ub.abl.payment.domain.*;
import id.ac.ub.abl.payment.adapter.outbound.database.entity.IdempotencyState;
import id.ac.ub.abl.payment.adapter.outbound.database.mapper.PaymentPersistenceMapper;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import id.ac.ub.abl.payment.usecase.model.*;
import id.ac.ub.abl.payment.usecase.port.out.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

/** Implementasi boundary finalisasi lokal. Encoder harus murni; tidak ada lookup HTTP di sini. */
@Component
public class PaymentCreationPersistenceAdapter implements PaymentCreationCommitter {
    private final PaymentsRepository payments;
    private final PaymentOrderGuardsRepository guards;
    private final PaymentIdempotencyRepository idempotency;
    private final PaymentPersistenceMapper mapper;
    private final PaymentDatabaseSession session;
    private final PaymentTransactions transactions;
    private final ObjectProvider<CreationResponseEncoder> encoder;
    public PaymentCreationPersistenceAdapter(PaymentsRepository payments, PaymentOrderGuardsRepository guards,
            PaymentIdempotencyRepository idempotency, PaymentPersistenceMapper mapper,
            PaymentDatabaseSession session, PaymentTransactions transactions, ObjectProvider<CreationResponseEncoder> encoder) {
        this.payments = payments; this.guards = guards; this.idempotency = idempotency; this.mapper = mapper;
        this.session = session; this.transactions = transactions; this.encoder = encoder;
    }
    public CreatePaymentResult.Created commit(IdempotencyClaim claim, OrderSnapshot order) {
        return transactions.creation(() -> {
            var row = transactions.claimLock(() -> idempotency.findByIdForUpdate(PaymentIdempotencyAdapter.id(claim.request())))
                    .orElseThrow(() -> conflict("REQUEST_IN_PROGRESS"));
            if (row.getState() != IdempotencyState.PROCESSING || !row.getOwnerToken().equals(claim.ownerToken())
                    || !row.getLeaseUntil().isAfter(session.now())) { throw conflict("REQUEST_IN_PROGRESS"); }
            if (!PaymentIdempotencyAdapter.matches(row, claim.request())) { throw conflict("IDEMPOTENCY_KEY_REUSED"); }
            if (!claim.request().orderId().equals(order.id())) {
                throw new PaymentOperationException("ORDER_UPSTREAM_ERROR", "ID snapshot Order tidak sesuai request");
            }
            Payment pending = Payment.create(order, claim.request().method());
            guards.insertIfAbsent(order.id());
            guards.findByOrderIdForUpdate(order.id()).orElseThrow();
            payments.findBlockingAttempt(order.id()).ifPresent(existing -> {
                throw conflict(existing.getStatus() == PaymentStatus.SUCCESS ? "ORDER_ALREADY_PAID" : "PAYMENT_ALREADY_PENDING");
            });
            var entity = payments.saveAndFlush(mapper.newEntity(pending, session.now()));
            Payment saved = mapper.toDomain(entity);
            StoredCreationResponse response = encoder.getObject().encode(saved);
            var completedAt = session.now();
            row.recordCompletion(saved.getId(), response.statusCode(), response.contentType(), response.body(),
                    completedAt, completedAt.plus(Duration.ofHours(24)));
            idempotency.flush();
            return new CreatePaymentResult.Created(saved, response);
        });
    }
    private PaymentOperationException conflict(String code) { return new PaymentOperationException(code, "Operasi Payment ditolak: " + code); }
}
