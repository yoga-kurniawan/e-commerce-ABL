package id.ac.ub.abl.product.usecase.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.product.usecase.command.*;
import id.ac.ub.abl.product.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.product.usecase.port.out.ProductStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductUseCasesTest {
    private final RecordingStore store = new RecordingStore();

    @Test
    void deleteDeactivatesProductAndSubsequentOperationsCannotFindIt() {
        store.product = fixture();
        new DeleteProductUseCase(store).execute(7L);
        assertFalse(store.product.isActive());
        assertEquals("Keyboard", store.product.getName());
        assertEquals(1, store.deletes);
        assertTrue(new ListProductsUseCase(store).execute().isEmpty());
        assertThrows(ProductNotFoundException.class, () -> new GetProductUseCase(store).execute(7L));
        assertThrows(ProductNotFoundException.class, () -> new DeleteProductUseCase(store).execute(7L));
        assertThrows(ProductNotFoundException.class, () -> new ReplaceProductUseCase(store).execute(
                new ReplaceProductCommand(7L, "Mouse", BigDecimal.ONE, 1)));
        assertThrows(ProductNotFoundException.class, () -> new PatchProductUseCase(store).execute(
                new PatchProductCommand(7L, PatchField.absent(), PatchField.absent(), PatchField.supplied(1))));
        assertEquals(0, store.saves);
        assertEquals(1, store.deletes);
    }

    @Test
    void deleteMissingProductDoesNotWrite() {
        assertThrows(ProductNotFoundException.class, () -> new DeleteProductUseCase(store).execute(7L));
        assertEquals(0, store.deletes);
        assertEquals(0, store.saves);
    }

    @Test
    void createValidatesDomainAndReturnsSavedProductWithId() {
        var result = new CreateProductUseCase(store).execute(
                new CreateProductCommand("Keyboard", new BigDecimal("350000.125"), 20));
        assertEquals(7L, result.getId());
        assertEquals("Keyboard", result.getName());
        assertEquals(new BigDecimal("350000.125"), result.getPrice());
        assertEquals(20, result.getStock());
        assertEquals(1, store.saves);
    }

    @Test
    void invalidCreateNeverWritesToStore() {
        var useCase = new CreateProductUseCase(store);
        assertThrows(DomainRuleViolation.class, () -> useCase.execute(
                new CreateProductCommand("Keyboard", new BigDecimal("-1"), 20)));
        assertEquals(0, store.saves);
    }

    @Test
    void listReturnsEmptyOrStoredProductsWithoutWriting() {
        var useCase = new ListProductsUseCase(store);
        assertTrue(useCase.execute().isEmpty());
        store.product = fixture();
        assertEquals(List.of(store.product), useCase.execute());
        assertEquals(0, store.saves);
    }

    @Test
    void getReturnsRequestedProductAndRejectsMissingId() {
        store.product = fixture();
        var useCase = new GetProductUseCase(store);
        assertSame(store.product, useCase.execute(7L));
        assertThrows(ProductNotFoundException.class, () -> useCase.execute(8L));
        assertEquals(0, store.saves);
    }

    @Test
    void replaceUpdatesAllAttributesAndKeepsIdentity() {
        store.product = fixture();
        var result = new ReplaceProductUseCase(store).execute(
                new ReplaceProductCommand(7L, "Mouse", BigDecimal.ZERO, 0));
        assertEquals(7L, result.getId());
        assertEquals("Mouse", result.getName());
        assertEquals(BigDecimal.ZERO, result.getPrice());
        assertEquals(0, result.getStock());
        assertEquals(1, store.saves);
    }

    @Test
    void incompleteReplaceDoesNotSaveOrChangeExistingProduct() {
        store.product = fixture();
        var previous = store.product;
        assertThrows(DomainRuleViolation.class, () -> new ReplaceProductUseCase(store).execute(
                new ReplaceProductCommand(7L, "Mouse", null, 0)));
        assertSame(previous, store.product);
        assertEquals(0, store.saves);
    }

    @Test
    void replaceMissingProductDoesNotCreateIt() {
        assertThrows(ProductNotFoundException.class, () -> new ReplaceProductUseCase(store).execute(
                new ReplaceProductCommand(7L, "Mouse", BigDecimal.ONE, 1)));
        assertEquals(0, store.saves);
    }

    @Test
    void patchChangesPriceAndPreservesOmittedNameAndStock() {
        store.product = fixture();
        var result = new PatchProductUseCase(store).execute(new PatchProductCommand(7L,
                PatchField.absent(), PatchField.supplied(new BigDecimal("375000.125")), PatchField.absent()));
        assertEquals(7L, result.getId());
        assertEquals("Keyboard", result.getName());
        assertEquals(new BigDecimal("375000.125"), result.getPrice());
        assertEquals(20, result.getStock());
        assertEquals(1, store.saves);
    }

    @Test
    void patchAllowsStockZeroAndNameChange() {
        store.product = fixture();
        var result = new PatchProductUseCase(store).execute(new PatchProductCommand(7L,
                PatchField.supplied("New name"), PatchField.absent(), PatchField.supplied(0)));
        assertEquals("New name", result.getName());
        assertEquals(0, result.getStock());
        assertEquals(new BigDecimal("350000"), result.getPrice());
    }

    @Test
    void emptyPatchIsRejectedWithoutWriting() {
        store.product = fixture();
        assertThrows(DomainRuleViolation.class, () -> new PatchProductUseCase(store).execute(
                new PatchProductCommand(7L, PatchField.absent(), PatchField.absent(), PatchField.absent())));
        assertEquals(0, store.saves);
    }

    @Test
    void explicitNullAndNegativePatchValuesDoNotChangeStoredProduct() {
        store.product = fixture();
        var previous = store.product;
        var useCase = new PatchProductUseCase(store);
        List<PatchProductCommand> invalid = List.of(
                new PatchProductCommand(7L, PatchField.supplied(null), PatchField.absent(), PatchField.absent()),
                new PatchProductCommand(7L, PatchField.absent(), PatchField.supplied(null), PatchField.absent()),
                new PatchProductCommand(7L, PatchField.absent(), PatchField.absent(), PatchField.supplied(null)),
                new PatchProductCommand(7L, PatchField.absent(), PatchField.supplied(new BigDecimal("-1")), PatchField.absent()),
                new PatchProductCommand(7L, PatchField.supplied("New name"), PatchField.absent(), PatchField.supplied(-1)));
        for (var command : invalid) {
            assertThrows(DomainRuleViolation.class, () -> useCase.execute(command));
        }
        assertSame(previous, store.product);
        assertEquals(0, store.saves);
    }

    @Test
    void patchMissingProductDoesNotCreateIt() {
        assertThrows(ProductNotFoundException.class, () -> new PatchProductUseCase(store).execute(
                new PatchProductCommand(7L, PatchField.absent(), PatchField.absent(), PatchField.supplied(0))));
        assertEquals(0, store.saves);
    }

    private static Product fixture() {
        return Product.restore(7L, "Keyboard", new BigDecimal("350000"), 20);
    }

    private static class RecordingStore implements ProductStore {
        Product product;
        int saves;
        int deletes;

        public Product save(Product value) {
            saves++;
            product = value.getId() == null
                    ? Product.restore(7L, value.getName(), value.getPrice(), value.getStock()) : value;
            return product;
        }

        public Optional<Product> findById(Long id) {
            return product != null && product.isActive() && product.getId().equals(id)
                    ? Optional.of(product) : Optional.empty();
        }

        public List<Product> findAll() {
            return product == null || !product.isActive() ? List.of() : List.of(product);
        }

        public void delete(Product value) {
            assertFalse(value.isActive());
            deletes++;
            product = value;
        }
    }
}
