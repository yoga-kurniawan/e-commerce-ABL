package id.ac.ub.abl.payment.adapter.outbound.database.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Identitas database berscope operasi; equality Java case-sensitive seperti collation C. */
@Embeddable
public class PaymentIdempotencyId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(nullable = false, length = 32, updatable = false)
    private String operation;

    @Column(name = "key", nullable = false, length = 128, updatable = false)
    private String key;

    protected PaymentIdempotencyId() { }

    public PaymentIdempotencyId(String operation, String key) {
        this.operation = Objects.requireNonNull(operation);
        this.key = Objects.requireNonNull(key);
    }

    public String getOperation() { return operation; }
    public String getKey() { return key; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PaymentIdempotencyId that
                && Objects.equals(operation, that.getOperation()) && Objects.equals(key, that.getKey());
    }

    @Override
    public int hashCode() { return Objects.hash(operation, key); }
}
