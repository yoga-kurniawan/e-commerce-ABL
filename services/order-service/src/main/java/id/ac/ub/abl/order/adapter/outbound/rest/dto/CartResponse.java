package id.ac.ub.abl.order.adapter.outbound.rest.dto;

import java.math.BigDecimal;
import java.util.List;

/** DTO respons remote GET /api/v1/carts/{cartId} dari Cart Service. */
public record CartResponse(Long id, Long customerId, List<CartItemResponse> items, BigDecimal total) {
}
