package id.ac.ub.abl.cart.adapter.inbound.rest.dto;

import java.math.BigDecimal;

/** Representasi publik CartItem sesuai kontrak OpenAPI. */
public record CartItemOutput(Long productId, int quantity, BigDecimal subtotal) {
}
