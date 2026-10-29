package id.ac.ub.abl.payment.adapter.exception;

import java.util.Map;
import id.ac.ub.abl.payment.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.payment.domain.exception.OrderNotPayableException;
import id.ac.ub.abl.payment.domain.exception.PaymentNotModifiableException;
import id.ac.ub.abl.payment.domain.exception.PaymentResultConflictException;
import id.ac.ub.abl.payment.usecase.exception.InvalidPaymentCommandException;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private record Failure(int status, String message) { }
    private static final Map<String, Failure> APPLICATION_ERRORS = Map.ofEntries(
            Map.entry("BAD_REQUEST", new Failure(400, "Format atau nilai permintaan tidak valid")),
            Map.entry("NOT_FOUND", new Failure(404, "Payment tidak ditemukan")),
            Map.entry("ORDER_NOT_FOUND", new Failure(404, "Order tidak ditemukan")),
            Map.entry("ORDER_NOT_PAYABLE", new Failure(409, "Order tidak dalam status yang dapat dibayar")),
            Map.entry("PAYMENT_ALREADY_PENDING", new Failure(409, "Order masih memiliki Payment PENDING")),
            Map.entry("ORDER_ALREADY_PAID", new Failure(409, "Order sudah memiliki Payment SUCCESS")),
            Map.entry("IDEMPOTENCY_KEY_REUSED", new Failure(409, "Key sudah dipakai untuk payload berbeda")),
            Map.entry("REQUEST_IN_PROGRESS", new Failure(409, "Request masih diproses; retry dengan key yang sama setelah jeda")),
            Map.entry("PAYMENT_OPERATION_BUSY", new Failure(409, "Operasi Payment sedang diproses; coba kembali setelah jeda")),
            Map.entry("PAYMENT_NOT_MODIFIABLE", new Failure(409, "Reference hanya dapat diubah selama Payment PENDING")),
            Map.entry("PAYMENT_RESULT_CONFLICT", new Failure(409, "Hasil bertentangan dengan status final Payment")),
            Map.entry("ORDER_UPSTREAM_ERROR", new Failure(502, "Respons Order Service tidak sesuai kontrak")),
            Map.entry("DEPENDENCY_UNAVAILABLE", new Failure(503, "Dependency belum tersedia atau hasil belum pasti; retry create dengan key yang sama")),
            Map.entry("ORDER_TIMEOUT", new Failure(504, "Koneksi atau pembacaan Order melewati timeout")));

    @ExceptionHandler(PaymentOperationException.class)
    public ResponseEntity<ApiError> operation(PaymentOperationException exception) {
        return application(exception.getCode());
    }

    @ExceptionHandler({InvalidPaymentCommandException.class, DomainRuleViolation.class})
    public ResponseEntity<ApiError> invalidInput(RuntimeException exception) {
        return application("BAD_REQUEST");
    }

    @ExceptionHandler(OrderNotPayableException.class)
    public ResponseEntity<ApiError> orderNotPayable(OrderNotPayableException exception) {
        return application("ORDER_NOT_PAYABLE");
    }

    @ExceptionHandler(PaymentNotModifiableException.class)
    public ResponseEntity<ApiError> notModifiable(PaymentNotModifiableException exception) {
        return application("PAYMENT_NOT_MODIFIABLE");
    }

    @ExceptionHandler(PaymentResultConflictException.class)
    public ResponseEntity<ApiError> resultConflict(PaymentResultConflictException exception) {
        return application("PAYMENT_RESULT_CONFLICT");
    }

    private ResponseEntity<ApiError> application(String code) {
        Failure failure = code == null ? null : APPLICATION_ERRORS.get(code);
        // Jangan meneruskan code/message arbitrer dari dependency ke client.
        if (failure == null) { return internalError(); }
        return ResponseEntity.status(failure.status()).contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(code, failure.message()));
    }

    /** Dipanggil resolver framework untuk parse/validation/path/header/media type/route, termasuk ErrorResponseException. */
    @Override protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var outputHeaders = new HttpHeaders();
        outputHeaders.putAll(headers);
        outputHeaders.setContentType(MediaType.APPLICATION_JSON);
        var knownStatus = HttpStatus.resolve(status.value());
        String code = knownStatus == null ? "HTTP_ERROR" : knownStatus.name();
        String message = switch (status.value()) {
            case 400 -> "Format atau nilai permintaan tidak valid";
            case 401 -> "Akses tidak terautentikasi";
            case 403 -> "Akses tidak diizinkan";
            case 404 -> "Resource tidak ditemukan";
            case 405 -> "Metode HTTP tidak didukung";
            case 406 -> "Format respons tidak didukung";
            case 415 -> "Content-Type tidak didukung";
            default -> status.is5xxServerError() ? "Terjadi kesalahan pada server" : "Permintaan tidak dapat diproses";
        };
        return super.handleExceptionInternal(exception, new ApiError(code, message), outputHeaders, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        // Message, cause, request body dan header dapat memuat token. Catat tipe saja.
        LOG.error("Kesalahan internal Payment, tipe={}", exception.getClass().getName());
        return internalError();
    }
    private ResponseEntity<ApiError> internalError() {
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("INTERNAL_SERVER_ERROR", "Terjadi kesalahan pada server"));
    }
}
