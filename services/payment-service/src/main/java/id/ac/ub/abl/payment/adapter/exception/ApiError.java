package id.ac.ub.abl.payment.adapter.exception;

/** Bentuk error publik sesuai kontrak, tanpa nested envelope atau detail exception. */
public record ApiError(String code, String message) { }
