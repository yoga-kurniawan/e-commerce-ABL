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
import id.ac.ub.abl.order.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.order.usecase.exception.CartServiceUnavailableException;
import id.ac.ub.abl.order.usecase.exception.EmptyCartException;
import id.ac.ub.abl.order.usecase.exception.OrderCancellationConflictException;
import id.ac.ub.abl.order.usecase.exception.OrderNotFoundException;
import id.ac.ub.abl.order.usecase.exception.PaymentServiceUnavailableException;
import id.ac.ub.abl.order.usecase.service.CancelOrderUseCase;
import id.ac.ub.abl.order.usecase.service.CreateOrderUseCase;
import id.ac.ub.abl.order.usecase.service.GetOrderUseCase;
import id.ac.ub.abl.order.usecase.service.ListOrdersUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrdersApiV2Controller.class)
@Import({OrderHttpMapper.class, GlobalExceptionHandler.class})
class OrdersApiV2ControllerSliceTest {
    private static final String BASE = "/api/v2/orders";

    @Autowired MockMvc http;

    @MockitoBean CreateOrderUseCase createOrder;
    @MockitoBean GetOrderUseCase getOrder;
    @MockitoBean ListOrdersUseCase listOrders;
    @MockitoBean CancelOrderUseCase cancelOrder;

    private static Order order(Long id, OrderStatus status) {
        return Order.restore(id, 101L, 10L, status, null, List.of(
                OrderItem.create(1L, "Keyboard", 2, new BigDecimal("350000")),
                OrderItem.create(2L, "Mouse", 1, new BigDecimal("150000.125"))));
    }

    @Test
    void createOrder_returns201WithRelativeLocationAndItems() throws Exception {
        when(createOrder.execute(new CreateOrderCommand(10L, 101L))).thenReturn(order(501L, OrderStatus.PENDING));

        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":10,\"customerId\":101}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v2/orders/501"))
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.customerId").value(101))
                .andExpect(jsonPath("$.total").value(850000.125))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.shippingAddress").isEmpty())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Keyboard"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(350000))
                .andExpect(jsonPath("$.items[0].subtotal").value(700000))
                .andExpect(jsonPath("$.items[1].unitPrice").value(150000.125))
                .andExpect(jsonPath("$.cartId").doesNotExist());
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
    void createOrder_mapsErrorsLikeV1() throws Exception {
        when(createOrder.execute(new CreateOrderCommand(99L, 101L))).thenThrow(new CartNotFoundException(99L));
        when(createOrder.execute(new CreateOrderCommand(11L, 101L))).thenThrow(new EmptyCartException(11L));
        when(createOrder.execute(new CreateOrderCommand(12L, 101L)))
                .thenThrow(new CartServiceUnavailableException("down", new RuntimeException()));

        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":99,\"customerId\":101}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":11,\"customerId\":101}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        http.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"cartId\":12,\"customerId\":101}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void getOrder_returns200WithItemsOr404() throws Exception {
        when(getOrder.execute(501L)).thenReturn(order(501L, OrderStatus.PENDING));
        when(getOrder.execute(999L)).thenThrow(new OrderNotFoundException(999L));

        http.perform(get(BASE + "/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.items.length()").value(2));
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
    void listOrders_returnsOrdersInUseCaseOrder() throws Exception {
        when(listOrders.execute(101L)).thenReturn(List.of(
                order(503L, OrderStatus.CANCELLED), order(501L, OrderStatus.PENDING)));

        http.perform(get(BASE).param("customerId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(503))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[1].id").value(501))
                .andExpect(jsonPath("$[1].items.length()").value(2));
    }

    @Test
    void listOrders_withoutOrders_returnsEmptyArray() throws Exception {
        when(listOrders.execute(202L)).thenReturn(List.of());

        http.perform(get(BASE).param("customerId", "202"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listOrders_withMissingOrInvalidCustomerId_returns400() throws Exception {
        http.perform(get(BASE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        http.perform(get(BASE).param("customerId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verify(listOrders, never()).execute(anyLong());
    }

    @Test
    void patchPutAndDeleteAreNotProvidedYet() throws Exception {
        http.perform(patch(BASE + "/501").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\":\"Jl. Veteran\"}"))
                .andExpect(status().isMethodNotAllowed());
        http.perform(put(BASE + "/501").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        http.perform(delete(BASE + "/501")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void cancelOrder_returns200WithCancelledOrder() throws Exception {
        when(cancelOrder.execute(501L)).thenReturn(order(501L, OrderStatus.CANCELLED));

        http.perform(post(BASE + "/501/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void cancelOrder_ignoresRequestBody() throws Exception {
        when(cancelOrder.execute(501L)).thenReturn(order(501L, OrderStatus.CANCELLED));

        http.perform(post(BASE + "/501/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_mapsNotFoundConflictAndUnavailable() throws Exception {
        when(cancelOrder.execute(999L)).thenThrow(new OrderNotFoundException(999L));
        when(cancelOrder.execute(502L)).thenThrow(
                new OrderNotModifiableException("Pesanan berstatus CONFIRMED tidak dapat dibatalkan"));
        when(cancelOrder.execute(503L)).thenThrow(
                new OrderCancellationConflictException("Pesanan masih memiliki pembayaran PENDING"));
        when(cancelOrder.execute(504L)).thenThrow(
                new PaymentServiceUnavailableException("down", new RuntimeException()));

        http.perform(post(BASE + "/999/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        http.perform(post(BASE + "/502/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Pesanan berstatus CONFIRMED tidak dapat dibatalkan"));
        http.perform(post(BASE + "/503/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Pesanan masih memiliki pembayaran PENDING"));
        http.perform(post(BASE + "/504/cancel"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Layanan pembayaran tidak dapat dihubungi"));
    }

    @Test
    void cancelOrder_withNonIntegerIdOrWrongMethod_isRejected() throws Exception {
        http.perform(post(BASE + "/abc/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        http.perform(get(BASE + "/501/cancel")).andExpect(status().isMethodNotAllowed());
        verify(cancelOrder, never()).execute(anyLong());
    }
}
