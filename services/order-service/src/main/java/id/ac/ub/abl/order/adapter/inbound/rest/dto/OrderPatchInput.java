package id.ac.ub.abl.order.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;

/** Request PATCH /orders/{orderId}; hanya shippingAddress yang dapat diubah. */
public class OrderPatchInput {
    private String shippingAddress;
    private boolean shippingAddressSupplied;

    @JsonSetter(value = "shippingAddress", nulls = Nulls.SET)
    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
        this.shippingAddressSupplied = true;
    }

    public String getShippingAddress() { return shippingAddress; }
    public boolean hasShippingAddress() { return shippingAddressSupplied; }

    @JsonIgnore
    @AssertTrue(message = "PATCH harus memuat minimal satu atribut pesanan")
    public boolean isNonEmpty() {
        return shippingAddressSupplied;
    }

    @JsonIgnore
    @AssertTrue(message = "Alamat pengiriman tidak boleh kosong")
    public boolean isShippingAddressValid() {
        return !shippingAddressSupplied || (shippingAddress != null && !shippingAddress.isBlank());
    }
}
