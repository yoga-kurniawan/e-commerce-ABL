package id.ac.ub.abl.cart.adapter.exception;

/** Bentuk error publik sesuai kontrak OpenAPI. */
public record ApiError(String code, String message) {
}
