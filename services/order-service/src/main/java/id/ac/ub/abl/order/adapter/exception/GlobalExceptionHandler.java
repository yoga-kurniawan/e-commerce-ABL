package id.ac.ub.abl.order.adapter.exception;

import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.order.domain.exception.OrderNotModifiableException;
import id.ac.ub.abl.order.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.order.usecase.exception.CartOwnershipMismatchException;
import id.ac.ub.abl.order.usecase.exception.CartServiceUnavailableException;
import id.ac.ub.abl.order.usecase.exception.EmptyCartException;
import id.ac.ub.abl.order.usecase.exception.InsufficientStockException;
import id.ac.ub.abl.order.usecase.exception.OrderNotFoundException;
import id.ac.ub.abl.order.usecase.exception.ProductServiceUnavailableException;
import id.ac.ub.abl.order.usecase.exception.ProductUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> orderNotFound(OrderNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "Pesanan tidak ditemukan"));
    }

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ApiError> cartNotFound(CartNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "Keranjang tidak ditemukan"));
    }

    @ExceptionHandler({EmptyCartException.class, CartOwnershipMismatchException.class,
            ProductUnavailableException.class, InsufficientStockException.class,
            OrderNotModifiableException.class})
    public ResponseEntity<ApiError> conflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(DomainRuleViolation.class)
    public ResponseEntity<ApiError> domainRuleViolation(DomainRuleViolation exception) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(CartServiceUnavailableException.class)
    public ResponseEntity<ApiError> cartServiceUnavailable(CartServiceUnavailableException exception) {
        LOG.error("Layanan Cart tidak tersedia", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("SERVICE_UNAVAILABLE", "Layanan keranjang tidak dapat dihubungi"));
    }

    @ExceptionHandler(ProductServiceUnavailableException.class)
    public ResponseEntity<ApiError> productServiceUnavailable(ProductServiceUnavailableException exception) {
        LOG.error("Layanan Product tidak tersedia", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("SERVICE_UNAVAILABLE", "Layanan produk tidak dapat dihubungi"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(value -> value != null)
                .sorted().findFirst().orElse("Data permintaan tidak valid");
        return new ResponseEntity<>(new ApiError("BAD_REQUEST", message), headers, status);
    }

    /** Pertahankan status/header framework, termasuk Allow pada 405. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        String code = knownStatus == null ? "HTTP_ERROR" : knownStatus.name();
        String message = switch (status.value()) {
            case 400 -> "Format atau nilai permintaan tidak valid";
            case 404 -> "Data tidak ditemukan";
            case 405 -> "Metode HTTP tidak didukung";
            case 406 -> "Format respons tidak didukung";
            case 415 -> "Content-Type tidak didukung";
            default -> status.is5xxServerError()
                    ? "Terjadi kesalahan pada server" : "Permintaan tidak dapat diproses";
        };
        return super.handleExceptionInternal(exception, new ApiError(code, message), headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpectedException(Exception exception) {
        LOG.error("Kesalahan internal saat memproses request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_SERVER_ERROR", "Terjadi kesalahan pada server"));
    }
}
