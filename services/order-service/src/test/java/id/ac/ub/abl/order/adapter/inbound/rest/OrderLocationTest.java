package id.ac.ub.abl.order.adapter.inbound.rest;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderInput;
import id.ac.ub.abl.order.adapter.inbound.rest.mapper.OrderHttpMapper;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.domain.OrderItem;
import id.ac.ub.abl.order.domain.OrderStatus;
import id.ac.ub.abl.order.usecase.service.CreateOrderUseCase;
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
class OrderLocationTest {
    @Mock CreateOrderUseCase createOrder;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:18080/api/v1/orders",
            "http://localhost:18083/api/v1/orders",
            "https://shop.example/api/v1/orders"
    })
    void createOrder_whenProxyAddressDiffers_shouldResolveRelativeLocation(String externalUrl) {
        var request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.setServerName("127.0.0.1");
        request.setServerPort(80);
        request.setQueryString("debug=true");
        request.addHeader("X-Forwarded-Host", "untrusted.example");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(createOrder.execute(any())).thenReturn(Order.restore(501L, 101L, 10L, OrderStatus.PENDING, null,
                List.of(OrderItem.create(1L, "Keyboard", 2, new BigDecimal("350000")))));

        var v1 = new OrdersApiController(createOrder, null, null, new OrderHttpMapper());
        var v2 = new OrdersApiV2Controller(createOrder, null, null, null, null, new OrderHttpMapper());

        URI v1Location = v1.createOrder(new OrderInput(10L, 101L)).getHeaders().getLocation();
        URI v2Location = v2.createOrder(new OrderInput(10L, 101L)).getHeaders().getLocation();

        assertThat(v1Location).isEqualTo(URI.create("/api/v1/orders/501"));
        assertThat(URI.create(externalUrl).resolve(v1Location).toString()).isEqualTo(externalUrl + "/501");
        assertThat(v2Location).isEqualTo(URI.create("/api/v2/orders/501"));
    }
}
