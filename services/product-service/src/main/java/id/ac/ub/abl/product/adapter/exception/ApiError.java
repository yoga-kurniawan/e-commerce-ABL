package id.ac.ub.abl.product.adapter.exception;

/** Bentuk error publik sesuai kontrak OpenAPI. */
public record ApiError(String code, String message) {
}
