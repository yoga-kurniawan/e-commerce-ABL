package id.ac.ub.abl.cart.adapter.inbound.rest.dto;

import java.math.BigDecimal;
import java.util.List;

/** Representasi publik Cart sesuai kontrak OpenAPI. */
public record CartOutput(Long id, Long customerId, List<CartItemOutput> items, BigDecimal total) {
}
