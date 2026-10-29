package id.ac.ub.abl.product.adapter.inbound.rest.dto;

import java.math.BigDecimal;

/** Representasi publik Product; status aktif hanya digunakan secara internal. */
public record ProductOutput(Long id, String name, BigDecimal price, int stock) {
}
