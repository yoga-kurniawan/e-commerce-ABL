package id.ac.ub.abl.order.adapter.inbound.rest;

import java.math.BigDecimal;
import java.util.List;

import id.ac.ub.abl.order.adapter.exception.GlobalExceptionHandler;
import id.ac.ub.abl.order.adapter.inbound.rest.mapper.OrderHttpMapper;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.domain.OrderItem;
import id.ac.ub.abl.order.domain.OrderStatus;
import id.ac.ub.abl.order.domain.exception.OrderNotModifiableException;
import id.ac.ub.abl.order.usecase.command.CreateOrderCommand;
import id.ac.ub.abl.order.usecase.command.PatchOrderCommand;
import id.ac.ub.abl.order.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.order.usecase.exception.EmptyCartException;
import id.ac.ub.abl.order.usecase.exception.OrderNotFoundException;
import id.ac.ub.abl.order.usecase.service.CreateOrderUseCase;
import id.ac.ub.abl.order.usecase.service.GetOrderUseCase;
import id.ac.ub.abl.order.usecase.service.PatchOrderUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrdersApiController.class)
@Import({OrderHttpMapper.class, GlobalExceptionHandler.class})
class OrdersApiControllerSliceTest {
    private static final String BASE = "/api/v1/orders";

    @Autowired MockMvc http;

    @MockitoBean CreateOrderUseCase createOrder;
    @MockitoBean GetOrderUseCase getOrder;
    @MockitoBean PatchOrderUseCase patchOrder;

    private static Order order(String address) {
        return Order.restore(501L, 101L, 10L, OrderStatus.PENDING, address,
                List.of(OrderItem.create(1L, "Keyboard", 2, new BigDecimal("350000"))));
    }

    @Test
    void createOrder_returns201WithLocationAndBody() throws Exception {
        when(createOrder.execute(new CreateOrderCommand(10L, 101L))).thenReturn(order(null));

        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":10,\"customerId\":101}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/501"))
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.customerId").value(101))
                .andExpect(jsonPath("$.total").value(700000))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    @Test
    void createOrder_withMissingField_returns400WithoutCallingUseCase() throws Exception {
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("ID customer wajib diisi"));
        verify(createOrder, never()).execute(any());
    }

    @Test
    void createOrder_mapsCartErrors() throws Exception {
        when(createOrder.execute(new CreateOrderCommand(99L, 101L))).thenThrow(new CartNotFoundException(99L));
        when(createOrder.execute(new CreateOrderCommand(11L, 101L))).thenThrow(new EmptyCartException(11L));

        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":99,\"customerId\":101}"))
                .andExpect(status().isNotFound());
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":11,\"customerId\":101}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getOrder_returns200Or404() throws Exception {
        when(getOrder.execute(501L)).thenReturn(order("Jl. Veteran"));
        when(getOrder.execute(999L)).thenThrow(new OrderNotFoundException(999L));

        http.perform(get(BASE + "/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddress").value("Jl. Veteran"));
        http.perform(get(BASE + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Pesanan tidak ditemukan"));
    }

    @Test
    void getOrder_withNonIntegerId_returns400() throws Exception {
        http.perform(get(BASE + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void updateOrder_returns200WithUpdatedAddress() throws Exception {
        when(patchOrder.execute(any(PatchOrderCommand.class))).thenReturn(order("Jl. Veteran No. 10, Malang"));

        http.perform(patch(BASE + "/501").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\":\"Jl. Veteran No. 10, Malang\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddress").value("Jl. Veteran No. 10, Malang"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void updateOrder_withEmptyOrNullBody_returns400() throws Exception {
        for (String body : List.of("{}", "{\"shippingAddress\":null}", "{\"status\":\"CONFIRMED\"}")) {
            http.perform(patch(BASE + "/501").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(patchOrder, never()).execute(any());
    }

    @Test
    void updateOrder_whenNotPending_returns409() throws Exception {
        when(patchOrder.execute(any(PatchOrderCommand.class)))
                .thenThrow(new OrderNotModifiableException("Alamat pengiriman tidak dapat diubah pada pesanan berstatus CONFIRMED"));

        http.perform(patch(BASE + "/501").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\":\"Alamat baru\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void putAndDeleteAreNotProvided() throws Exception {
        http.perform(delete(BASE + "/501")).andExpect(status().isMethodNotAllowed());
        http.perform(put(BASE + "/501").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
