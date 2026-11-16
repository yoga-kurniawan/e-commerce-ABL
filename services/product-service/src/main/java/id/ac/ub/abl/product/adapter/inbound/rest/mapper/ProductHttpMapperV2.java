package id.ac.ub.abl.product.adapter.inbound.rest.mapper;

import id.ac.ub.abl.product.adapter.inbound.rest.dto.v2.*;
import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.usecase.command.*;
import org.springframework.stereotype.Component;

@Component
public class ProductHttpMapperV2 {
    public CreateProductCommand toCreateCommand(ProductInputV2 input) {
        return new CreateProductCommand(input.name(), input.price(), input.stock(), input.description());
    }

    public ReplaceProductCommand toReplaceCommand(Long id, ProductInputV2 input) {
        return new ReplaceProductCommand(id, input.name(), input.price(), input.stock(),
                PatchField.supplied(input.description()));
    }

    public PatchProductCommand toPatchCommand(Long id, ProductPatchInputV2 input) {
        return new PatchProductCommand(id,
                input.hasName() ? PatchField.supplied(input.getName()) : PatchField.absent(),
                input.hasPrice() ? PatchField.supplied(input.getPrice()) : PatchField.absent(),
                input.hasStock() ? PatchField.supplied(input.getStock()) : PatchField.absent(),
                input.hasDescription() ? PatchField.supplied(input.getDescription()) : PatchField.absent());
    }

    public ProductOutputV2 toOutput(Product p) {
        return new ProductOutputV2(p.getId(), p.getName(), p.getPrice(), p.getStock(), p.getDescription());
    }
}
