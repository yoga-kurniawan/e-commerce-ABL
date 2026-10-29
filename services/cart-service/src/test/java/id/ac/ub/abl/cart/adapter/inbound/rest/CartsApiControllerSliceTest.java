package id.ac.ub.abl.cart.adapter.inbound.rest;

import java.math.BigDecimal;
import java.util.List;

import id.ac.ub.abl.cart.adapter.exception.GlobalExceptionHandler;
import id.ac.ub.abl.cart.adapter.inbound.rest.mapper.CartHttpMapper;
import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.CartItem;
import id.ac.ub.abl.cart.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.cart.domain.exception.InsufficientStockException;
import id.ac.ub.abl.cart.usecase.exception.CartItemNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.cart.usecase.service.AddCartItemUseCase;
import id.ac.ub.abl.cart.usecase.service.CreateCartUseCase;
import id.ac.ub.abl.cart.usecase.service.DeleteCartItemUseCase;
import id.ac.ub.abl.cart.usecase.service.GetCartUseCase;
import id.ac.ub.abl.cart.usecase.service.PatchCartItemUseCase;
import id.ac.ub.abl.cart.usecase.service.ReplaceCartItemUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartsApiController.class)
@Import({CartHttpMapper.class, GlobalExceptionHandler.class})
class CartsApiControllerSliceTest {
    private static final String BASE = "/api/v1/carts";

    @Autowired MockMvc http;

    @MockitoBean CreateCartUseCase createCart;
    @MockitoBean GetCartUseCase getCart;
    @MockitoBean AddCartItemUseCase addCartItem;
    @MockitoBean ReplaceCartItemUseCase replaceCartItem;
    @MockitoBean PatchCartItemUseCase patchCartItem;
    @MockitoBean DeleteCartItemUseCase deleteCartItem;

    @Test
    void createCart_withValidCustomerId_returns201WithLocation() throws Exception {
        Cart cart = Cart.restore(10L, 101L, List.of());
        when(createCart.execute(any())).thenReturn(cart);

        http.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":101}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/carts/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.customerId").value(101))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void createCart_withoutCustomerId_returns400() throws Exception {
        http.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void getCart_whenExists_returns200() throws Exception {
        Cart cart = Cart.restore(10L, 101L, List.of(
                CartItem.restore(1L, 2, new BigDecimal("350000"))
        ));
        when(getCart.execute(10L)).thenReturn(cart);

        http.perform(get(BASE + "/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.customerId").value(101))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(700000))
                .andExpect(jsonPath("$.total").value(700000));
    }

    @Test
    void getCart_whenNotFound_returns404() throws Exception {
        when(getCart.execute(999L)).thenThrow(new CartNotFoundException(999L));

        http.perform(get(BASE + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Keranjang tidak ditemukan"));
    }

    @Test
    void addItem_whenValid_returns201WithLocation() throws Exception {
        Cart cart = Cart.restore(10L, 101L, List.of(
                CartItem.restore(1L, 2, new BigDecimal("350000"))
        ));
        when(addCartItem.execute(any())).thenReturn(cart);

        http.perform(post(BASE + "/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/carts/10/items/1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(700000));
    }

    @Test
    void addItem_whenStockInsufficient_returns409() throws Exception {
        when(addCartItem.execute(any())).thenThrow(new InsufficientStockException("Jumlah melebihi stok yang tersedia"));

        http.perform(post(BASE + "/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Jumlah melebihi stok yang tersedia"));
    }

    @Test
    void addItem_whenProductNotFound_returns404() throws Exception {
        when(addCartItem.execute(any())).thenThrow(new ProductNotFoundException(999L));

        http.perform(post(BASE + "/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":999,\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Produk tidak ditemukan"));
    }

    @Test
    void addItem_withZeroQuantity_returns400() throws Exception {
        http.perform(post(BASE + "/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void replaceItem_whenValid_returns200() throws Exception {
        Cart cart = Cart.restore(10L, 101L, List.of(
                CartItem.restore(1L, 4, new BigDecimal("350000"))
        ));
        when(replaceCartItem.execute(any())).thenReturn(cart);

        http.perform(put(BASE + "/10/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(10))
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.subtotal").value(1400000));
    }

    @Test
    void replaceItem_whenItemNotFound_returns404() throws Exception {
        when(replaceCartItem.execute(any())).thenThrow(new CartItemNotFoundException(10L, 999L));

        http.perform(put(BASE + "/10/items/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updateItem_whenValid_returns200() throws Exception {
        Cart cart = Cart.restore(10L, 101L, List.of(
                CartItem.restore(1L, 3, new BigDecimal("350000"))
        ));
        when(patchCartItem.execute(any())).thenReturn(cart);

        http.perform(patch(BASE + "/10/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value(10))
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.subtotal").value(1050000));
    }

    @Test
    void updateItem_whenEmpty_returns400() throws Exception {
        http.perform(patch(BASE + "/10/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void deleteItem_whenValid_returns204NoContent() throws Exception {
        doNothing().when(deleteCartItem).execute(10L, 1L);

        http.perform(delete(BASE + "/10/items/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deleteItem_whenNotFound_returns404() throws Exception {
        doThrow(new CartItemNotFoundException(10L, 999L)).when(deleteCartItem).execute(10L, 999L);

        http.perform(delete(BASE + "/10/items/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
