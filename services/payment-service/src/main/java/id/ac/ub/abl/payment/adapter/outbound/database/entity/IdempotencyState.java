package id.ac.ub.abl.payment.adapter.outbound.database.entity;

/** State catatan operasi create, terpisah dari PaymentStatus. */
public enum IdempotencyState {
    PROCESSING, COMPLETED
}
