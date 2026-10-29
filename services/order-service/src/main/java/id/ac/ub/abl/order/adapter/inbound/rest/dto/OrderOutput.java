package id.ac.ub.abl.order.adapter.inbound.rest.dto;

import java.math.BigDecimal;

/** Representasi publik Order; shippingAddress null sampai diisi melalui PATCH. */
public record OrderOutput(Long id, Long customerId, BigDecimal total, String status, String shippingAddress) {
}
