package id.ac.ub.abl.product.adapter.inbound.rest.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;

/** Presence disimpan terpisah dari nilai agar null eksplisit tidak dianggap absen. */
public class ProductPatchInput {
    private String name;
    private BigDecimal price;
    private Integer stock;
    private boolean nameSupplied;
    private boolean priceSupplied;
    private boolean stockSupplied;

    @JsonSetter(value = "name", nulls = Nulls.SET)
    public void setName(String name) {
        this.name = name;
        this.nameSupplied = true;
    }

    @JsonSetter(value = "price", nulls = Nulls.SET)
    public void setPrice(BigDecimal price) {
        this.price = price;
        this.priceSupplied = true;
    }

    @JsonSetter(value = "stock", nulls = Nulls.SET)
    public void setStock(Integer stock) {
        this.stock = stock;
        this.stockSupplied = true;
    }

    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public Integer getStock() { return stock; }
    public boolean hasName() { return nameSupplied; }
    public boolean hasPrice() { return priceSupplied; }
    public boolean hasStock() { return stockSupplied; }

    @JsonIgnore
    @AssertTrue(message = "PATCH harus memuat minimal satu atribut produk")
    public boolean isNonEmpty() {
        return nameSupplied || priceSupplied || stockSupplied;
    }

    @JsonIgnore
    @AssertTrue(message = "Nama produk tidak boleh null")
    public boolean isNameValid() {
        return !nameSupplied || name != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Harga produk wajib bernilai tidak negatif")
    public boolean isPriceValid() {
        return !priceSupplied || (price != null && price.signum() >= 0);
    }

    @JsonIgnore
    @AssertTrue(message = "Stok produk wajib bernilai tidak negatif")
    public boolean isStockValid() {
        return !stockSupplied || (stock != null && stock >= 0);
    }
}
