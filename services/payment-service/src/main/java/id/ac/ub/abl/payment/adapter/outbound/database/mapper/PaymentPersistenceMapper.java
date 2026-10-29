package id.ac.ub.abl.payment.adapter.outbound.database.mapper;

import java.time.Instant;
import java.util.Objects;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.adapter.outbound.database.entity.PaymentJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentPersistenceMapper {
    public Payment toDomain(PaymentJpaEntity entity) {
        return Payment.restore(entity.getId(), entity.getOrderId(), entity.getMethod(), entity.getAmount(),
                entity.getStatus(), entity.getTransactionReference());
    }
    public PaymentJpaEntity newEntity(Payment payment, Instant now) {
        if (payment.getId() != null) { throw new IllegalArgumentException("Gunakan managed entity untuk update"); }
        return new PaymentJpaEntity(payment.getOrderId(), payment.getMethod(), payment.getAmount(),
                payment.getStatus(), payment.getTransactionReference(), now);
    }
    /** Tidak merekonstruksi entity existing, sehingga id/version/createdAt tetap terpelihara. */
    public void updateManaged(PaymentJpaEntity entity, Payment payment, Instant now) {
        if (!Objects.equals(entity.getId(), payment.getId()) || !Objects.equals(entity.getOrderId(), payment.getOrderId())
                || entity.getMethod() != payment.getMethod() || entity.getAmount().compareTo(payment.getAmount()) != 0) {
            throw new IllegalArgumentException("Identitas dan amount Payment immutable");
        }
        if (entity.getStatus() != payment.getStatus() || !Objects.equals(entity.getTransactionReference(), payment.getTransactionReference())) {
            entity.updateState(payment.getStatus(), payment.getTransactionReference(), now);
        }
    }
}
