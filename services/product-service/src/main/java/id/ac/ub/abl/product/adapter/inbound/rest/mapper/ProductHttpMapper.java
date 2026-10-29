package id.ac.ub.abl.product.adapter.inbound.rest.mapper;

import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductInput;
import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductOutput;
import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductPatchInput;
import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.usecase.command.CreateProductCommand;
import id.ac.ub.abl.product.usecase.command.PatchField;
import id.ac.ub.abl.product.usecase.command.PatchProductCommand;
import id.ac.ub.abl.product.usecase.command.ReplaceProductCommand;
import org.springframework.stereotype.Component;

@Component
public class ProductHttpMapper {
    public CreateProductCommand toCreateCommand(ProductInput input) {
        return new CreateProductCommand(input.name(), input.price(), input.stock());
    }

    public ReplaceProductCommand toReplaceCommand(Long productId, ProductInput input) {
        return new ReplaceProductCommand(productId, input.name(), input.price(), input.stock());
    }

    public PatchProductCommand toPatchCommand(Long productId, ProductPatchInput input) {
        return new PatchProductCommand(productId,
                input.hasName() ? PatchField.supplied(input.getName()) : PatchField.absent(),
                input.hasPrice() ? PatchField.supplied(input.getPrice()) : PatchField.absent(),
                input.hasStock() ? PatchField.supplied(input.getStock()) : PatchField.absent());
    }

    public ProductOutput toOutput(Product product) {
        return new ProductOutput(product.getId(), product.getName(), product.getPrice(), product.getStock());
    }
}
