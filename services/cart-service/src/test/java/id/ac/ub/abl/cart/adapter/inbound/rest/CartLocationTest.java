package id.ac.ub.abl.cart.adapter.inbound.rest;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.mapper.CartHttpMapper;
import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.CartItem;
import id.ac.ub.abl.cart.usecase.service.AddCartItemUseCase;
import id.ac.ub.abl.cart.usecase.service.CreateCartUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartLocationTest {
    @Mock CreateCartUseCase createCart;
    @Mock AddCartItemUseCase addCartItem;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:18080/api/v1/carts",
            "http://localhost:18082/api/v1/carts",
            "https://shop.example/api/v1/carts"
    })
    void createCartAndAddItem_whenProxyAddressDiffers_shouldResolveRelativeLocation(String externalUrl) {
        var request = new MockHttpServletRequest("POST", "/api/v1/carts");
        request.setServerName("127.0.0.1");
        request.setServerPort(80);
        request.setQueryString("debug=true");
        request.addHeader("X-Forwarded-Host", "untrusted.example");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(createCart.execute(any())).thenReturn(Cart.restore(10L, 101L, List.of()));
        when(addCartItem.execute(any())).thenReturn(Cart.restore(10L, 101L,
                List.of(CartItem.restore(7L, 2, new BigDecimal("350000")))));

        var v1 = new CartsApiController(createCart, null, addCartItem, null, null, null, new CartHttpMapper());
        var v2 = new CartsApiV2Controller(createCart, null, addCartItem, null, null, null, new CartHttpMapper(), null);

        URI v1CartLocation = v1.createCart(new CartInput(101L)).getHeaders().getLocation();
        URI v1ItemLocation = v1.addItem(10L, new CartItemInput(7L, 2)).getHeaders().getLocation();
        URI v2CartLocation = v2.createCart(new CartInput(101L)).getHeaders().getLocation();
        URI v2ItemLocation = v2.addItem(10L, new CartItemInput(7L, 2)).getHeaders().getLocation();

        assertThat(v1CartLocation).isEqualTo(URI.create("/api/v1/carts/10"));
        assertThat(v1ItemLocation).isEqualTo(URI.create("/api/v1/carts/10/items/7"));
        assertThat(URI.create(externalUrl).resolve(v1CartLocation).toString()).isEqualTo(externalUrl + "/10");
        assertThat(URI.create(externalUrl).resolve(v1ItemLocation).toString()).isEqualTo(externalUrl + "/10/items/7");

        assertThat(v2CartLocation).isEqualTo(URI.create("/api/v2/carts/10"));
        assertThat(v2ItemLocation).isEqualTo(URI.create("/api/v2/carts/10/items/7"));
    }
}
