package id.ac.ub.abl.order.adapter.inbound.rest.dto.v2;

import java.math.BigDecimal;
import java.util.List;

public record OrderOutputV2(Long id, Long customerId, BigDecimal total, String status,
                            String shippingAddress, List<OrderItemOutputV2> items) {
}
