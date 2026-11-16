package id.ac.ub.abl.product.adapter.inbound.rest.dto.v2;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonInclude;

/** description is present even when null; V1 continues to use ProductOutput. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductOutputV2(Long id, String name, BigDecimal price, int stock, String description) {
}
