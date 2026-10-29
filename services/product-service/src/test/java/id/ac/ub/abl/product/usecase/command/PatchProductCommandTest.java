package id.ac.ub.abl.product.usecase.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PatchProductCommandTest {
    @Test
    void distinguishesZeroStockFromAnOmittedField() {
        var command = new PatchProductCommand(7L,
                PatchField.absent(), PatchField.absent(), PatchField.supplied(0));

        assertTrue(command.hasChanges());
        assertFalse(command.name().supplied());
        assertFalse(command.price().supplied());
        assertTrue(command.stock().supplied());
        assertEquals(0, command.stock().value());
    }

    @Test
    void preservesExplicitNullInsteadOfTreatingItAsOmitted() {
        var command = new PatchProductCommand(7L,
                PatchField.supplied(null), PatchField.absent(), PatchField.absent());

        assertTrue(command.hasChanges());
        assertTrue(command.name().supplied());
        assertNull(command.name().value());
    }

    @Test
    void identifiesAnEmptyPatchForValidationByTheUseCase() {
        var command = new PatchProductCommand(7L,
                PatchField.absent(), PatchField.absent(), PatchField.absent());

        assertFalse(command.hasChanges());
    }

    @Test
    void rejectsAmbiguousFieldRepresentations() {
        assertThrows(IllegalArgumentException.class, () -> new PatchField<>(false, 10));
        assertThrows(NullPointerException.class, () -> new PatchProductCommand(7L,
                null, PatchField.absent(), PatchField.absent()));
    }
}
