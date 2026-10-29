package id.ac.ub.abl.order.adapter.outbound.rest.mapper;

import id.ac.ub.abl.order.adapter.outbound.rest.dto.ProductResponse;
import id.ac.ub.abl.order.domain.ProductSnapshot;
import org.springframework.stereotype.Component;

@Component
public class ProductRestMapper {
    public ProductSnapshot toSnapshot(ProductResponse response) {
        return new ProductSnapshot(response.id(), response.name(), response.price(), response.stock());
    }
}
