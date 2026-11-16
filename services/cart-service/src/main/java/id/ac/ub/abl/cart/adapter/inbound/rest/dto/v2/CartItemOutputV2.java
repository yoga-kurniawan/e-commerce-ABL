package id.ac.ub.abl.cart.adapter.inbound.rest.dto.v2;

import java.math.BigDecimal;

public record CartItemOutputV2(
        Long productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {}

File CartOutputV2.java:

package id.ac.ub.abl.cart.adapter.inbound.rest.dto.v2;

import java.math.BigDecimal;
import java.util.List;

public record CartOutputV2(
        Long id,
        Long customerId,
        List<CartItemOutputV2> items,
        BigDecimal total
) {}