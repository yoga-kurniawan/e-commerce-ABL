package id.ac.ub.abl.order.adapter.outbound.rest.dto;

import java.math.BigDecimal;

/** DTO item keranjang remote dari Cart Service. */
public record CartItemResponse(Long productId, int quantity, BigDecimal subtotal) {
}
