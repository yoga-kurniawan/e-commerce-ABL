package id.ac.ub.abl.payment.adapter.outbound.database.entity;

import java.time.Instant;

import id.ac.ub.abl.payment.domain.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Mapping klaim dan snapshot. Tidak menjalankan protokol claim/takeover/commit. */
@Entity
@Table(name = "payment_idempotency")
public class PaymentIdempotencyJpaEntity {
    @EmbeddedId
    private PaymentIdempotencyId id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private PaymentMethod method;

    @Column(nullable = false, length = 64, updatable = false)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IdempotencyState state;

    @Column(name = "owner_token", nullable = false, columnDefinition = "text")
    private String ownerToken;

    @Column(name = "lease_until", nullable = false)
    private Instant leaseUntil;

    // FK lokal ditangani Flyway; scalar ID menghindari graph/proxy yang tidak diperlukan.
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_content_type", columnDefinition = "text")
    private String responseContentType;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PaymentIdempotencyJpaEntity() { }

    public PaymentIdempotencyJpaEntity(PaymentIdempotencyId id, Long orderId, PaymentMethod method,
                                       String fingerprint, String ownerToken, Instant leaseUntil) {
        this.id = id;
        this.orderId = orderId;
        this.method = method;
        this.fingerprint = fingerprint;
        this.state = IdempotencyState.PROCESSING;
        this.ownerToken = ownerToken;
        this.leaseUntil = leaseUntil;
    }

    /** Assignment penyimpanan; caller wajib memverifikasi lock/state/lease/owner lebih dahulu. */
    public void updateOwner(String ownerToken, Instant leaseUntil) {
        this.ownerToken = ownerToken;
        this.leaseUntil = leaseUntil;
    }

    /** Assignment snapshot; atomicity dan validasi dimiliki transaksi adapter tahap berikutnya. */
    public void recordCompletion(Long paymentId, Integer responseStatus, String responseContentType,
                                 String responseBody, Instant completedAt, Instant expiresAt) {
        this.state = IdempotencyState.COMPLETED;
        this.paymentId = paymentId;
        this.responseStatus = responseStatus;
        this.responseContentType = responseContentType;
        this.responseBody = responseBody;
        this.completedAt = completedAt;
        this.expiresAt = expiresAt;
    }

    public PaymentIdempotencyId getId() { return id; }
    public Long getOrderId() { return orderId; }
    public PaymentMethod getMethod() { return method; }
    public String getFingerprint() { return fingerprint; }
    public IdempotencyState getState() { return state; }
    public String getOwnerToken() { return ownerToken; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public Long getPaymentId() { return paymentId; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseContentType() { return responseContentType; }
    public String getResponseBody() { return responseBody; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Long getVersion() { return version; }
}
