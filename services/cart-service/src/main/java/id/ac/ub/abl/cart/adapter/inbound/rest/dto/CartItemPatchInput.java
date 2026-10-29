package id.ac.ub.abl.cart.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;

/** Request PATCH /carts/{cartId}/items/{productId} dengan pelacakan kehadiran field. */
public class CartItemPatchInput {
    private Integer quantity;
    private boolean quantitySupplied;

    @JsonSetter(value = "quantity", nulls = Nulls.SET)
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
        this.quantitySupplied = true;
    }

    public Integer getQuantity() { return quantity; }
    public boolean hasQuantity() { return quantitySupplied; }

    @JsonIgnore
    @AssertTrue(message = "PATCH harus memuat minimal satu atribut")
    public boolean isNonEmpty() {
        return quantitySupplied;
    }

    @JsonIgnore
    @AssertTrue(message = "Quantity tidak boleh null")
    public boolean isQuantityNotNull() {
        return !quantitySupplied || quantity != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Quantity minimal 1")
    public boolean isQuantityPositive() {
        return !quantitySupplied || (quantity != null && quantity >= 1);
    }
}
