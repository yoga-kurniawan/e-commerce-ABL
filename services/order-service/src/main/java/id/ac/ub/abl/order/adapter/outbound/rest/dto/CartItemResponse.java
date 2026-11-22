package id.ac.ub.abl.order.adapter.outbound.rest.dto;

import java.math.BigDecimal;

/** DTO item keranjang remote dari Cart Service v2 (unitPrice opsional dengan fallback subtotal / quantity). */
public record CartItemResponse(Long productId, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
}
