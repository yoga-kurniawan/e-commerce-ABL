package id.ac.ub.abl.payment.adapter.outbound.database.entity;

import java.math.BigDecimal;
import java.time.Instant;

import id.ac.ub.abl.payment.domain.PaymentMethod;
import id.ac.ub.abl.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** State penyimpanan saja; invariant bisnis tetap dimiliki domain Payment. */
@Entity
@Table(name = "payments")
public class PaymentJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payments_id")
    @SequenceGenerator(name = "payments_id", sequenceName = "payments_seq", allocationSize = 50)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(nullable = false, columnDefinition = "numeric", updatable = false)
    private BigDecimal amount;

    @Column(name = "transaction_reference", columnDefinition = "text")
    private String transactionReference;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentJpaEntity() { }

    /** now berasal dari clock database pada transaksi adapter, bukan clock node aplikasi. */
    public PaymentJpaEntity(Long orderId, PaymentMethod method, BigDecimal amount,
                            PaymentStatus status, String transactionReference, Instant now) {
        this.orderId = orderId;
        this.method = method;
        this.amount = amount;
        this.status = status;
        this.transactionReference = transactionReference;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Dipanggil adapter setelah domain memvalidasi perubahan pada entity managed. */
    public void updateState(PaymentStatus status, String transactionReference, Instant updatedAt) {
        this.status = status;
        this.transactionReference = transactionReference;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getTransactionReference() { return transactionReference; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
