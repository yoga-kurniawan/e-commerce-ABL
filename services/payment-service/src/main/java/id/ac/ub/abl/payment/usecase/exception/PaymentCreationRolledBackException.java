package id.ac.ub.abl.payment.usecase.exception;

/** Hanya dilempar committer setelah transaction completion mengonfirmasi rollback. */
public class PaymentCreationRolledBackException extends RuntimeException {
    public PaymentCreationRolledBackException(RuntimeException failure) { super(failure.getMessage(), failure); }
    public RuntimeException failure() { return (RuntimeException) getCause(); }
}
