package id.ac.ub.abl.product.adapter.inbound.rest.dto.v2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;
import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductPatchInput;

public class ProductPatchInputV2 extends ProductPatchInput {
    private String description;
    private boolean descriptionSupplied;

    @JsonSetter(value = "description", nulls = Nulls.SET)
    public void setDescription(String description) {
        this.description = description;
        this.descriptionSupplied = true;
    }

    public String getDescription() { return description; }
    public boolean hasDescription() { return descriptionSupplied; }

    @Override
    @JsonIgnore
    @AssertTrue(message = "PATCH harus memuat minimal satu atribut produk")
    public boolean isNonEmpty() { return super.isNonEmpty() || descriptionSupplied; }

    @JsonIgnore
    @AssertTrue(message = "Deskripsi produk maksimal 1000 karakter")
    public boolean isDescriptionValid() { return description == null || description.length() <= 1000; }
}
