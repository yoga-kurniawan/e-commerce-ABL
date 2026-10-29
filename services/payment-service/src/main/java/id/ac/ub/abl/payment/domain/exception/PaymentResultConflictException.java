package id.ac.ub.abl.payment.domain.exception;

public class PaymentResultConflictException extends RuntimeException {
    public PaymentResultConflictException() {
        super("Hasil pembayaran bertentangan dengan status final yang tersimpan");
    }
}
