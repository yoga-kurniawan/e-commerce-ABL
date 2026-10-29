package id.ac.ub.abl.cart.adapter.outbound.rest.dto;

import java.math.BigDecimal;

/** DTO respons remote dari Product Service. */
public record ProductResponse(Long id, String name, BigDecimal price, int stock) {
}
