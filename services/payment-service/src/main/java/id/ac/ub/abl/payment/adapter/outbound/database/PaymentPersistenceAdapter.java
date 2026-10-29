package id.ac.ub.abl.payment.adapter.outbound.database;

import java.util.Optional;
import java.util.function.UnaryOperator;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.usecase.command.ApplyPaymentResultCommand;
import id.ac.ub.abl.payment.usecase.command.PatchPaymentCommand;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import id.ac.ub.abl.payment.usecase.port.out.PaymentStore;
import id.ac.ub.abl.payment.adapter.outbound.database.mapper.PaymentPersistenceMapper;
import org.springframework.stereotype.Component;

@Component
public class PaymentPersistenceAdapter implements PaymentStore {
    private final PaymentsRepository payments;
    private final PaymentOrderGuardsRepository guards;
    private final PaymentPersistenceMapper mapper;
    private final PaymentDatabaseSession session;
    private final PaymentTransactions transactions;
    public PaymentPersistenceAdapter(PaymentsRepository payments, PaymentOrderGuardsRepository guards,
            PaymentPersistenceMapper mapper, PaymentDatabaseSession session, PaymentTransactions transactions) {
        this.payments = payments; this.guards = guards; this.mapper = mapper;
        this.session = session; this.transactions = transactions;
    }
    public Optional<Payment> findById(Long id) {
        return transactions.run("PAYMENT_OPERATION_BUSY", () -> payments.findById(id).map(mapper::toDomain));
    }
    public Payment changeReference(PatchPaymentCommand command) {
        return mutate(command.paymentId(), payment -> payment.changeReference(command.transactionReference()));
    }
    public Payment applyResult(ApplyPaymentResultCommand command) {
        return mutate(command.paymentId(), payment -> payment.applyResult(command.result()));
    }
    private Payment mutate(Long id, UnaryOperator<Payment> change) {
        return transactions.run("PAYMENT_OPERATION_BUSY", () -> {
            Long orderId = payments.findOrderIdById(id).orElseThrow(this::missing);
            guards.insertIfAbsent(orderId);
            guards.findByOrderIdForUpdate(orderId).orElseThrow();
            var entity = payments.findByIdForUpdate(id).orElseThrow(this::missing);
            Payment changed = change.apply(mapper.toDomain(entity));
            mapper.updateManaged(entity, changed, session.now());
            payments.flush();
            return changed;
        });
    }
    private PaymentOperationException missing() { return new PaymentOperationException("NOT_FOUND", "Payment tidak ditemukan"); }
}
