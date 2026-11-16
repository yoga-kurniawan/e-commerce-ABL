package id.ac.ub.abl.order.adapter.inbound.rest.dto.v2;

import java.math.BigDecimal;

public record OrderItemOutputV2(Long productId, String productName, int quantity,
                                BigDecimal unitPrice, BigDecimal subtotal) {
}
