package id.ac.ub.abl.product.adapter.inbound.rest.dto.v2;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductInputV2(
        @NotNull String name,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotNull @PositiveOrZero Integer stock,
        @Size(max = 1000) String description) {
}
