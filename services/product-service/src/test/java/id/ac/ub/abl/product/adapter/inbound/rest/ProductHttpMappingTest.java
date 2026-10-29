package id.ac.ub.abl.product.adapter.inbound.rest;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductInput;
import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductPatchInput;
import id.ac.ub.abl.product.adapter.inbound.rest.mapper.ProductHttpMapper;
import id.ac.ub.abl.product.domain.Product;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class ProductHttpMappingTest {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATION.getValidator();
    private final JsonMapper json = JsonMapper.builder().build();
    private final ProductHttpMapper mapper = new ProductHttpMapper();

    @AfterAll
    static void closeValidator() {
        VALIDATION.close();
    }

    @Test
    void createAndReplaceMapAllRequiredValuesWithoutRounding() {
        var input = json.readValue(
                "{\"name\":\"Keyboard\",\"price\":350000.125,\"stock\":20}", ProductInput.class);
        assertTrue(VALIDATOR.validate(input).isEmpty());

        var create = mapper.toCreateCommand(input);
        var replace = mapper.toReplaceCommand(7L, input);
        assertEquals("Keyboard", create.name());
        assertEquals(new BigDecimal("350000.125"), create.price());
        assertEquals(20, create.stock());
        assertEquals(7L, replace.productId());
        assertEquals(create.name(), replace.name());
        assertEquals(create.price(), replace.price());
        assertEquals(create.stock(), replace.stock());
    }

    @Test
    void inputAllowsZeroPriceAndStock() {
        var input = json.readValue("{\"name\":\"Sample\",\"price\":0,\"stock\":0}", ProductInput.class);
        assertTrue(VALIDATOR.validate(input).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"price\":1,\"stock\":1}",
            "{\"name\":\"Keyboard\",\"stock\":1}",
            "{\"name\":\"Keyboard\",\"price\":1}",
            "{\"name\":null,\"price\":1,\"stock\":1}",
            "{\"name\":\"Keyboard\",\"price\":null,\"stock\":1}",
            "{\"name\":\"Keyboard\",\"price\":1,\"stock\":null}",
            "{\"name\":\"Keyboard\",\"price\":-0.01,\"stock\":1}",
            "{\"name\":\"Keyboard\",\"price\":1,\"stock\":-1}"
    })
    void inputRejectsMissingNullAndNegativeValues(String body) {
        assertFalse(VALIDATOR.validate(json.readValue(body, ProductInput.class)).isEmpty());
    }

    @Test
    void pricePatchPreservesPresenceAndDecimalValue() {
        var input = json.readValue("{\"price\":375000.125}", ProductPatchInput.class);
        assertTrue(VALIDATOR.validate(input).isEmpty());
        var command = mapper.toPatchCommand(7L, input);
        assertEquals(7L, command.productId());
        assertFalse(command.name().supplied());
        assertTrue(command.price().supplied());
        assertEquals(new BigDecimal("375000.125"), command.price().value());
        assertFalse(command.stock().supplied());
    }

    @Test
    void patchAllowsZeroAndMultipleFields() {
        var input = json.readValue("{\"name\":\"Mouse\",\"price\":0,\"stock\":0}", ProductPatchInput.class);
        assertTrue(VALIDATOR.validate(input).isEmpty());
        var command = mapper.toPatchCommand(7L, input);
        assertEquals("Mouse", command.name().value());
        assertEquals(BigDecimal.ZERO, command.price().value());
        assertEquals(0, command.stock().value());
        assertTrue(command.stock().supplied());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"price\":null}", "{\"stock\":null}",
            "{\"price\":-0.01}", "{\"stock\":-1}"})
    void patchRejectsEmptyNullAndNegativeValues(String body) {
        assertFalse(VALIDATOR.validate(json.readValue(body, ProductPatchInput.class)).isEmpty());
    }

    @Test
    void patchMappingDoesNotLoseExplicitNull() {
        var command = mapper.toPatchCommand(7L,
                json.readValue("{\"stock\":null}", ProductPatchInput.class));
        assertTrue(command.stock().supplied());
        assertNull(command.stock().value());
        assertFalse(command.name().supplied());
    }

    @Test
    void outputMatchesPublicSchemaAndDoesNotExposeInternalState() {
        var product = Product.restore(7L, "Keyboard", new BigDecimal("350000.125"), 20, false);
        var output = mapper.toOutput(product);
        var body = json.readTree(json.writeValueAsString(output));
        Set<String> fields = new HashSet<>();
        body.propertyNames().forEach(fields::add);
        assertEquals(Set.of("id", "name", "price", "stock"), fields);
        assertEquals(7L, body.get("id").longValue());
        assertEquals("Keyboard", body.get("name").stringValue());
        assertEquals(0, new BigDecimal("350000.125").compareTo(output.price()));
        assertTrue(body.get("price").isNumber());
        assertEquals(20, body.get("stock").intValue());
    }
}
