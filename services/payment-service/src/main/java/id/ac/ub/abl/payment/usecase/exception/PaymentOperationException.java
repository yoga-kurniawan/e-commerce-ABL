package id.ac.ub.abl.payment.usecase.exception;

/** Kegagalan operasi aplikasi; mapping HTTP dilakukan inbound adapter pada tahap berikutnya. */
public class PaymentOperationException extends RuntimeException {
    private final String code;
    public PaymentOperationException(String code, String message) { super(message); this.code = code; }
    public PaymentOperationException(String code, String message, Throwable cause) {
        super(message, cause); this.code = code;
    }
    public String getCode() { return code; }
}
