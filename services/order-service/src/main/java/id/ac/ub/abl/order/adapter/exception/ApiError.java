package id.ac.ub.abl.order.adapter.exception;

/** Bentuk error publik sesuai kontrak OpenAPI. */
public record ApiError(String code, String message) {
}
