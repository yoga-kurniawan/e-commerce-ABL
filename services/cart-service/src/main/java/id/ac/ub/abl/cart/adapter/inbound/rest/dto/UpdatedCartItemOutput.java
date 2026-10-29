package id.ac.ub.abl.cart.adapter.inbound.rest.dto;

import java.math.BigDecimal;

/** Representasi respons setelah memperbarui item (PUT/PATCH). */
public record UpdatedCartItemOutput(Long cartId, Long productId, int quantity, BigDecimal subtotal) {
}
