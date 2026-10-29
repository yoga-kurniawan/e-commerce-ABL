package id.ac.ub.abl.payment.adapter.outbound.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Koordinasi lokal per Order, tanpa association/FK ke Order Service. */
@Entity
@Table(name = "payment_order_guards")
public class PaymentOrderGuardJpaEntity {
    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PaymentOrderGuardJpaEntity() { }

    public PaymentOrderGuardJpaEntity(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() { return orderId; }
    public Long getVersion() { return version; }
}
