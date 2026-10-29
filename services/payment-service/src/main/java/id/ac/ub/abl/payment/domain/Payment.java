package id.ac.ub.abl.payment.domain;

import java.math.BigDecimal;

import id.ac.ub.abl.payment.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.payment.domain.exception.PaymentNotModifiableException;
import id.ac.ub.abl.payment.domain.exception.PaymentResultConflictException;

/** Model immutable: perubahan valid menghasilkan instance baru, tanpa HTTP/JPA. */
public final class Payment {
    private final Long id;
    private final Long orderId;
    private final PaymentMethod method;
    private final BigDecimal amount;
    private final PaymentStatus status;
    private final String transactionReference;

    private Payment(Long id, Long orderId, PaymentMethod method, BigDecimal amount,
                    PaymentStatus status, String transactionReference) {
        if (orderId == null || method == null || status == null) {
            throw new DomainRuleViolation("Order, metode, dan status Payment wajib diisi");
        }
        if (amount == null || amount.signum() < 0) {
            throw new DomainRuleViolation("Amount wajib diisi dan tidak boleh negatif");
        }
        if (transactionReference != null) {
            validateReference(transactionReference);
        }
        this.id = id;
        this.orderId = orderId;
        this.method = method;
        this.amount = amount;
        this.status = status;
        this.transactionReference = transactionReference;
    }

    /** Total diambil dari snapshot backend Order, bukan input nominal client. */
    public static Payment create(OrderSnapshot order, PaymentMethod method) {
        if (order == null) {
            throw new DomainRuleViolation("Snapshot Order wajib tersedia");
        }
        order.requirePayable();
        return new Payment(null, order.id(), method, order.total(), PaymentStatus.PENDING, null);
    }

    /** Rehidrasi state tersimpan, bukan operasi create bisnis atau verifikasi provider. */
    public static Payment restore(Long id, Long orderId, PaymentMethod method, BigDecimal amount,
                                  PaymentStatus status, String transactionReference) {
        if (id == null) {
            throw new DomainRuleViolation("ID Payment tersimpan wajib diisi");
        }
        return new Payment(id, orderId, method, amount, status, transactionReference);
    }

    public Payment changeReference(String reference) {
        if (status != PaymentStatus.PENDING) {
            throw new PaymentNotModifiableException();
        }
        validateReference(reference);
        return new Payment(id, orderId, method, amount, status, reference);
    }

    /** Dipanggil use case hasil terpercaya/simulasi, bukan PATCH publik. */
    public Payment applyResult(PaymentStatus result) {
        if (result == null || result == PaymentStatus.PENDING) {
            throw new DomainRuleViolation("Hasil pembayaran harus SUCCESS atau FAILED");
        }
        if (status == result) {
            return this;
        }
        if (status != PaymentStatus.PENDING) {
            throw new PaymentResultConflictException();
        }
        return new Payment(id, orderId, method, amount, result, transactionReference);
    }

    private static void validateReference(String reference) {
        if (reference == null || reference.codePoints().noneMatch(codePoint ->
                !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))) {
            throw new DomainRuleViolation("Reference tidak boleh null, kosong, atau hanya whitespace");
        }
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public PaymentMethod getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getTransactionReference() { return transactionReference; }
}
