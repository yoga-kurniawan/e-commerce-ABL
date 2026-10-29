package id.ac.ub.abl.cart.usecase.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.domain.ProductSnapshot;
import id.ac.ub.abl.cart.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.cart.domain.exception.InsufficientStockException;
import id.ac.ub.abl.cart.usecase.command.AddCartItemCommand;
import id.ac.ub.abl.cart.usecase.command.CreateCartCommand;
import id.ac.ub.abl.cart.usecase.command.PatchCartItemCommand;
import id.ac.ub.abl.cart.usecase.command.PatchField;
import id.ac.ub.abl.cart.usecase.command.ReplaceCartItemCommand;
import id.ac.ub.abl.cart.usecase.exception.CartItemNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.cart.usecase.exception.ProductNotFoundException;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import id.ac.ub.abl.cart.usecase.port.out.ProductLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CartUseCasesTest {
    private RecordingCartStore cartStore;
    private StubProductLookup productLookup;

    @BeforeEach
    void setUp() {
        cartStore = new RecordingCartStore();
        productLookup = new StubProductLookup();
    }

    @Test
    void createCart_savesAndReturnsCartWithId() {
        var useCase = new CreateCartUseCase(cartStore);
        Cart cart = useCase.execute(new CreateCartCommand(101L));

        assertNotNull(cart.getId());
        assertEquals(101L, cart.getCustomerId());
        assertTrue(cart.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, cart.getTotal());
        assertEquals(1, cartStore.saves);
    }

    @Test
    void getCart_returnsExistingCart() {
        Cart existing = cartStore.save(Cart.create(101L));
        var useCase = new GetCartUseCase(cartStore);

        Cart loaded = useCase.execute(existing.getId());

        assertEquals(existing.getId(), loaded.getId());
        assertEquals(101L, loaded.getCustomerId());
    }

    @Test
    void getCart_whenNotFound_throwsCartNotFoundException() {
        var useCase = new GetCartUseCase(cartStore);
        assertThrows(CartNotFoundException.class, () -> useCase.execute(999L));
    }

    @Test
    void addCartItem_newProduct_success() {
        Cart cart = cartStore.save(Cart.create(101L));
        productLookup.products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10));

        var useCase = new AddCartItemUseCase(cartStore, productLookup);
        Cart result = useCase.execute(new AddCartItemCommand(cart.getId(), 1L, 2));

        assertEquals(1, result.getItems().size());
        assertEquals(1L, result.getItems().get(0).getProductId());
        assertEquals(2, result.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("700000"), result.getTotal());
    }

    @Test
    void addCartItem_existingProduct_increasesQuantity() {
        Cart cart = cartStore.save(Cart.create(101L));
        productLookup.products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10));

        var useCase = new AddCartItemUseCase(cartStore, productLookup);
        useCase.execute(new AddCartItemCommand(cart.getId(), 1L, 2));

        // Tambah lagi produk yang sama dengan perubahan harga di katalog
        productLookup.products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("400000"), 10));
        Cart result = useCase.execute(new AddCartItemCommand(cart.getId(), 1L, 3));

        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getItems().get(0).getQuantity());
        // Harga satuan awal dipertahankan
        assertEquals(new BigDecimal("350000"), result.getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("1750000"), result.getTotal());
    }

    @Test
    void addCartItem_whenCartNotFound_throwsException() {
        productLookup.products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10));
        var useCase = new AddCartItemUseCase(cartStore, productLookup);

        assertThrows(CartNotFoundException.class, () -> useCase.execute(new AddCartItemCommand(999L, 1L, 2)));
    }

    @Test
    void addCartItem_whenProductNotFound_throwsException() {
        Cart cart = cartStore.save(Cart.create(101L));
        var useCase = new AddCartItemUseCase(cartStore, productLookup);

        assertThrows(ProductNotFoundException.class, () -> useCase.execute(new AddCartItemCommand(cart.getId(), 999L, 2)));
    }

    @Test
    void addCartItem_whenStockInsufficient_throwsException() {
        Cart cart = cartStore.save(Cart.create(101L));
        productLookup.products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 3));
        var useCase = new AddCartItemUseCase(cartStore, productLookup);

        assertThrows(InsufficientStockException.class, () -> useCase.execute(new AddCartItemCommand(cart.getId(), 1L, 5)));
    }

    @Test
    void replaceCartItem_success() {
        Cart cart = cartStore.save(Cart.create(101L));
        productLookup.products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10));
        new AddCartItemUseCase(cartStore, productLookup).execute(new AddCartItemCommand(cart.getId(), 1L, 2));

        var useCase = new ReplaceCartItemUseCase(cartStore, productLookup);
        Cart result = useCase.execute(new ReplaceCartItemCommand(cart.getId(), 1L, 5));

        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("1750000"), result.getTotal());
    }

    @Test
    void replaceCartItem_whenItemNotFound_throwsException() {
        Cart cart = cartStore.save(Cart.create(101L));
        productLookup.products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10));
        var useCase = new ReplaceCartItemUseCase(cartStore, productLookup);

        assertThrows(CartItemNotFoundException.class, () -> useCase.execute(new ReplaceCartItemCommand(cart.getId(), 1L, 5)));
    }

    @Test
    void replaceCartItem_whenStockInsufficient_throwsException() {
        Cart cart = cartStore.save(Cart.create(101L));
        productLookup.products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 4));
        new AddCartItemUseCase(cartStore, productLookup).execute(new AddCartItemCommand(cart.getId(), 1L, 2));

        var useCase = new ReplaceCartItemUseCase(cartStore, productLookup);
        assertThrows(InsufficientStockException.class, () -> useCase.execute(new ReplaceCartItemCommand(cart.getId(), 1L, 5)));
    }

    @Test
    void patchCartItem_success() {
        Cart cart = cartStore.save(Cart.create(101L));
        productLookup.products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10));
        new AddCartItemUseCase(cartStore, productLookup).execute(new AddCartItemCommand(cart.getId(), 1L, 2));

        var useCase = new PatchCartItemUseCase(cartStore, productLookup);
        Cart result = useCase.execute(new PatchCartItemCommand(cart.getId(), 1L, PatchField.supplied(4)));

        assertEquals(4, result.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("1400000"), result.getTotal());
    }

    @Test
    void patchCartItem_whenEmptyPatch_throwsDomainRuleViolation() {
        Cart cart = cartStore.save(Cart.create(101L));
        var useCase = new PatchCartItemUseCase(cartStore, productLookup);

        assertThrows(DomainRuleViolation.class, () ->
                useCase.execute(new PatchCartItemCommand(cart.getId(), 1L, PatchField.absent())));
    }

    @Test
    void patchCartItem_whenNullQuantity_throwsDomainRuleViolation() {
        Cart cart = cartStore.save(Cart.create(101L));
        var useCase = new PatchCartItemUseCase(cartStore, productLookup);

        assertThrows(DomainRuleViolation.class, () ->
                useCase.execute(new PatchCartItemCommand(cart.getId(), 1L, PatchField.supplied(null))));
    }

    @Test
    void deleteCartItem_success() {
        Cart cart = cartStore.save(Cart.create(101L));
        productLookup.products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10));
        productLookup.products.put(2L, new ProductSnapshot(2L, "Mouse", new BigDecimal("150000"), 10));
        new AddCartItemUseCase(cartStore, productLookup).execute(new AddCartItemCommand(cart.getId(), 1L, 2));
        new AddCartItemUseCase(cartStore, productLookup).execute(new AddCartItemCommand(cart.getId(), 2L, 1));

        var useCase = new DeleteCartItemUseCase(cartStore);
        useCase.execute(cart.getId(), 1L);

        Cart loaded = cartStore.findById(cart.getId()).orElseThrow();
        assertEquals(1, loaded.getItems().size());
        assertEquals(2L, loaded.getItems().get(0).getProductId());
        assertEquals(new BigDecimal("150000"), loaded.getTotal());
    }

    @Test
    void deleteCartItem_whenItemNotFound_throwsException() {
        Cart cart = cartStore.save(Cart.create(101L));
        var useCase = new DeleteCartItemUseCase(cartStore);

        assertThrows(CartItemNotFoundException.class, () -> useCase.execute(cart.getId(), 999L));
    }

    private static class RecordingCartStore implements CartStore {
        private final Map<Long, Cart> storage = new HashMap<>();
        private long idSequence = 1L;
        int saves = 0;

        @Override
        public Cart save(Cart cart) {
            saves++;
            Long id = cart.getId() != null ? cart.getId() : idSequence++;
            Cart stored = Cart.restore(id, cart.getCustomerId(), cart.getItems());
            storage.put(id, stored);
            return stored;
        }

        @Override
        public Optional<Cart> findById(Long cartId) {
            return Optional.ofNullable(storage.get(cartId));
        }

        @Override
        public void delete(Cart cart) {
            if (cart.getId() != null) {
                storage.remove(cart.getId());
            }
        }
    }

    private static class StubProductLookup implements ProductLookup {
        final Map<Long, ProductSnapshot> products = new HashMap<>();

        @Override
        public Optional<ProductSnapshot> findById(Long productId) {
            return Optional.ofNullable(products.get(productId));
        }
    }
}
