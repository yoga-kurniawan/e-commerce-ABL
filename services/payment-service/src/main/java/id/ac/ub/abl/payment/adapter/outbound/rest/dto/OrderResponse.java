package id.ac.ub.abl.payment.adapter.outbound.rest.dto;

import java.math.BigDecimal;

/** Hanya data remote yang dibutuhkan Payment, tanpa dependency module Order. */
public record OrderResponse(Long id, BigDecimal total, String status) { }
