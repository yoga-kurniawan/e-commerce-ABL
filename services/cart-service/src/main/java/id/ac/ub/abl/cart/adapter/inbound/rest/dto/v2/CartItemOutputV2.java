package id.ac.ub.abl.cart.adapter.inbound.rest.dto.v2;

import java.math.BigDecimal;

public record CartItemOutputV2(
        Long productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {}
