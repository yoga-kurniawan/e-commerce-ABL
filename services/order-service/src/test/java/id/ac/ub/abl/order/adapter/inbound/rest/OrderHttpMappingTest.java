package id.ac.ub.abl.order.adapter.inbound.rest;

import java.math.BigDecimal;
import java.util.List;

import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderInput;
import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderOutput;
import id.ac.ub.abl.order.adapter.inbound.rest.dto.OrderPatchInput;
import id.ac.ub.abl.order.adapter.inbound.rest.mapper.OrderHttpMapper;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.domain.OrderItem;
import id.ac.ub.abl.order.domain.OrderStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class OrderHttpMappingTest {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATION.getValidator();
    private final JsonMapper json = JsonMapper.builder().build();
    private final OrderHttpMapper mapper = new OrderHttpMapper();

    @AfterAll
    static void closeValidator() {
        VALIDATION.close();
    }

    @Test
    void orderInputMapsToCreateCommand() {
        var input = json.readValue("{\"cartId\":10,\"customerId\":101}", OrderInput.class);
        assertTrue(VALIDATOR.validate(input).isEmpty());

        var command = mapper.toCreateCommand(input);
        assertEquals(10L, command.cartId());
        assertEquals(101L, command.customerId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"cartId\":10}", "{\"customerId\":101}", "{\"cartId\":null,\"customerId\":101}"})
    void orderInputRejectsMissingReferences(String body) {
        assertFalse(VALIDATOR.validate(json.readValue(body, OrderInput.class)).isEmpty());
    }

    @Test
    void patchInputTracksPresenceOfShippingAddress() {
        var input = json.readValue("{\"shippingAddress\":\"Jl. Veteran No. 10, Malang\"}", OrderPatchInput.class);
        assertTrue(VALIDATOR.validate(input).isEmpty());

        var command = mapper.toPatchCommand(501L, input);
        assertEquals(501L, command.orderId());
        assertTrue(command.hasChanges());
        assertEquals("Jl. Veteran No. 10, Malang", command.shippingAddress().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"shippingAddress\":null}",
            "{\"shippingAddress\":\"   \"}",
            "{\"status\":\"CONFIRMED\"}",
            "{\"total\":1}"
    })
    void patchInputRejectsEmptyNullBlankAndNonEditableOnly(String body) {
        assertFalse(VALIDATOR.validate(json.readValue(body, OrderPatchInput.class)).isEmpty());
    }

    @Test
    void outputExposesContractFieldsOnly() {
        Order order = Order.restore(501L, 101L, 10L, OrderStatus.PENDING, null,
                List.of(OrderItem.create(1L, "Keyboard", 2, new BigDecimal("350000"))));

        OrderOutput output = mapper.toOutput(order);
        assertEquals(new OrderOutput(501L, 101L, new BigDecimal("700000"), "PENDING", null), output);

        var node = json.readTree(json.writeValueAsString(output));
        assertEquals(5, node.size());
        assertTrue(node.has("shippingAddress"));
        assertFalse(node.has("items"));
        assertFalse(node.has("cartId"));
    }
}
