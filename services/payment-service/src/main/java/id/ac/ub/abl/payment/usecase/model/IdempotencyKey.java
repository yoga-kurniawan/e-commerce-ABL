package id.ac.ub.abl.payment.usecase.model;

import java.util.regex.Pattern;
import id.ac.ub.abl.payment.usecase.exception.InvalidPaymentCommandException;

/** Identitas operasi dalam satu payment_db, bukan identifier resource Payment. */
public record IdempotencyKey(String value) {
    public static final String OPERATION = "CREATE_PAYMENT_V1";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public IdempotencyKey {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new InvalidPaymentCommandException("Idempotency-Key wajib berupa 1-128 karakter ASCII yang diizinkan");
        }
    }
}
