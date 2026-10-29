package id.ac.ub.abl.order.usecase.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PatchOrderCommandTest {

    @Test
    void absentFieldMeansNoChanges() {
        assertFalse(new PatchOrderCommand(1L, PatchField.absent()).hasChanges());
    }

    @Test
    void suppliedNullIsStillAChangeRequest() {
        PatchOrderCommand command = new PatchOrderCommand(1L, PatchField.supplied(null));
        assertTrue(command.hasChanges());
        assertNull(command.shippingAddress().value());
    }

    @Test
    void rejectsMissingPatchFieldAndAbsentWithValue() {
        assertThrows(NullPointerException.class, () -> new PatchOrderCommand(1L, null));
        assertThrows(IllegalArgumentException.class, () -> new PatchField<>(false, "x"));
    }
}
