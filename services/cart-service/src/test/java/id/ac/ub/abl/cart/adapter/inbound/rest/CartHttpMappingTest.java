package id.ac.ub.abl.cart.adapter.inbound.rest;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemPatchInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.dto.CartItemQuantityInput;
import id.ac.ub.abl.cart.adapter.inbound.rest.mapper.CartHttpMapper;
import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.CartItem;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class CartHttpMappingTest {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATION.getValidator();
    private final JsonMapper json = JsonMapper.builder().build();
    private final CartHttpMapper mapper = new CartHttpMapper();

    @AfterAll
    static void closeValidator() {
        VALIDATION.close();
    }

    @Test
    void cartInputValidatesCustomerId() {
        var valid = json.readValue("{\"customerId\":101}", CartInput.class);
        assertTrue(VALIDATOR.validate(valid).isEmpty());
        assertEquals(101L, mapper.toCreateCommand(valid).customerId());

        var invalid = json.readValue("{}", CartInput.class);
        assertFalse(VALIDATOR.validate(invalid).isEmpty());
    }

    @Test
    void cartItemInputValidatesProductIdAndQuantity() {
        var valid = json.readValue("{\"productId\":1,\"quantity\":2}", CartItemInput.class);
        assertTrue(VALIDATOR.validate(valid).isEmpty());
        var command = mapper.toAddCommand(10L, valid);
        assertEquals(10L, command.cartId());
        assertEquals(1L, command.productId());
        assertEquals(2, command.quantity());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"quantity\":2}",
            "{\"productId\":1}",
            "{\"productId\":1,\"quantity\":0}",
            "{\"productId\":1,\"quantity\":-1}"
    })
    void cartItemInputRejectsMissingAndInvalidQuantity(String body) {
        var input = json.readValue(body, CartItemInput.class);
        assertFalse(VALIDATOR.validate(input).isEmpty());
    }

    @Test
    void cartItemQuantityInputValidatesQuantity() {
        var valid = json.readValue("{\"quantity\":5}", CartItemQuantityInput.class);
        assertTrue(VALIDATOR.validate(valid).isEmpty());
        var command = mapper.toReplaceCommand(10L, 1L, valid);
        assertEquals(10L, command.cartId());
        assertEquals(1L, command.productId());
        assertEquals(5, command.quantity());

        var invalidZero = json.readValue("{\"quantity\":0}", CartItemQuantityInput.class);
        assertFalse(VALIDATOR.validate(invalidZero).isEmpty());

        var invalidEmpty = json.readValue("{}", CartItemQuantityInput.class);
        assertFalse(VALIDATOR.validate(invalidEmpty).isEmpty());
    }

    @Test
    void cartItemPatchInputTracksPresence() {
        var valid = json.readValue("{\"quantity\":3}", CartItemPatchInput.class);
        assertTrue(VALIDATOR.validate(valid).isEmpty());
        var command = mapper.toPatchCommand(10L, 1L, valid);
        assertTrue(command.hasChanges());
        assertEquals(3, command.quantity().value());

        var empty = json.readValue("{}", CartItemPatchInput.class);
        assertFalse(VALIDATOR.validate(empty).isEmpty());

        var nullVal = json.readValue("{\"quantity\":null}", CartItemPatchInput.class);
        assertFalse(VALIDATOR.validate(nullVal).isEmpty());

        var zeroVal = json.readValue("{\"quantity\":0}", CartItemPatchInput.class);
        assertFalse(VALIDATOR.validate(zeroVal).isEmpty());
    }

    @Test
    void outputMatchesPublicSchema() {
        Cart cart = Cart.restore(10L, 101L, List.of(
                CartItem.restore(1L, 2, new BigDecimal("350000"))
        ));
        var output = mapper.toOutput(cart);
        var body = json.readTree(json.writeValueAsString(output));

        Set<String> fields = new HashSet<>();
        body.propertyNames().forEach(fields::add);
        assertEquals(Set.of("id", "customerId", "items", "total"), fields);
        assertEquals(10L, body.get("id").longValue());
        assertEquals(101L, body.get("customerId").longValue());
        assertEquals(1, body.get("items").size());
        assertEquals(700000, body.get("total").intValue());

        var itemNode = body.get("items").get(0);
        Set<String> itemFields = new HashSet<>();
        itemNode.propertyNames().forEach(itemFields::add);
        assertEquals(Set.of("productId", "quantity", "subtotal"), itemFields);
        assertEquals(1L, itemNode.get("productId").longValue());
        assertEquals(2, itemNode.get("quantity").intValue());
        assertEquals(700000, itemNode.get("subtotal").intValue());
    }
}
