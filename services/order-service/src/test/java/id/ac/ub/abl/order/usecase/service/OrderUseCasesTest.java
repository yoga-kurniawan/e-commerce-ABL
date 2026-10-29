package id.ac.ub.abl.order.usecase.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import id.ac.ub.abl.order.domain.CartItemSnapshot;
import id.ac.ub.abl.order.domain.CartSnapshot;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.domain.OrderItem;
import id.ac.ub.abl.order.domain.OrderStatus;
import id.ac.ub.abl.order.domain.ProductSnapshot;
import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;
import id.ac.ub.abl.order.domain.exception.OrderNotModifiableException;
import id.ac.ub.abl.order.usecase.command.CreateOrderCommand;
import id.ac.ub.abl.order.usecase.command.PatchField;
import id.ac.ub.abl.order.usecase.command.PatchOrderCommand;
import id.ac.ub.abl.order.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.order.usecase.exception.CartOwnershipMismatchException;
import id.ac.ub.abl.order.usecase.exception.EmptyCartException;
import id.ac.ub.abl.order.usecase.exception.InsufficientStockException;
import id.ac.ub.abl.order.usecase.exception.OrderNotFoundException;
import id.ac.ub.abl.order.usecase.exception.ProductServiceUnavailableException;
import id.ac.ub.abl.order.usecase.exception.ProductUnavailableException;
import id.ac.ub.abl.order.usecase.port.out.CartLookup;
import id.ac.ub.abl.order.usecase.port.out.OrderStore;
import id.ac.ub.abl.order.usecase.port.out.ProductLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderUseCasesTest {
    private RecordingOrderStore orderStore;
    private Map<Long, CartSnapshot> carts;
    private Map<Long, ProductSnapshot> products;
    private CreateOrderUseCase createOrder;

    @BeforeEach
    void setUp() {
        orderStore = new RecordingOrderStore();
        carts = new HashMap<>();
        products = new HashMap<>();
        CartLookup cartLookup = cartId -> Optional.ofNullable(carts.get(cartId));
        ProductLookup productLookup = productId -> Optional.ofNullable(products.get(productId));
        createOrder = new CreateOrderUseCase(orderStore, cartLookup, productLookup);

        products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("350000"), 10));
        products.put(2L, new ProductSnapshot(2L, "Mouse", new BigDecimal("150000"), 10));
    }

    private void cart(Long cartId, Long customerId, CartItemSnapshot... items) {
        carts.put(cartId, new CartSnapshot(cartId, customerId, List.of(items)));
    }

    @Test
    void createOrder_snapshotsCartPricesAndProductNames() {
        cart(10L, 101L,
                new CartItemSnapshot(1L, 2, new BigDecimal("700000")),
                new CartItemSnapshot(2L, 1, new BigDecimal("150000")));

        Order order = createOrder.execute(new CreateOrderCommand(10L, 101L));

        assertNotNull(order.getId());
        assertEquals(101L, order.getCustomerId());
        assertEquals(10L, order.getCartId());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(0, new BigDecimal("850000").compareTo(order.getTotal()));
        assertEquals("Keyboard", order.getItems().get(0).getProductName());
        assertEquals(1, orderStore.saves);
    }

    @Test
    void createOrder_usesCartPriceEvenWhenCatalogPriceChanged() {
        products.put(1L, new ProductSnapshot(1L, "Keyboard", new BigDecimal("120000"), 10));
        cart(10L, 101L, new CartItemSnapshot(1L, 2, new BigDecimal("200000")));

        Order order = createOrder.execute(new CreateOrderCommand(10L, 101L));

        assertEquals(0, new BigDecimal("100000").compareTo(order.getItems().get(0).getUnitPrice()));
        assertEquals(0, new BigDecimal("200000").compareTo(order.getTotal()));
    }

    @Test
    void createOrder_whenCartMissing_throwsNotFound() {
        assertThrows(CartNotFoundException.class, () -> createOrder.execute(new CreateOrderCommand(99L, 101L)));
        assertEquals(0, orderStore.saves);
    }

    @Test
    void createOrder_whenCustomerDiffers_throwsMismatch() {
        cart(10L, 101L, new CartItemSnapshot(1L, 1, new BigDecimal("350000")));
        assertThrows(CartOwnershipMismatchException.class, () -> createOrder.execute(new CreateOrderCommand(10L, 202L)));
        assertEquals(0, orderStore.saves);
    }

    @Test
    void createOrder_whenCartEmpty_throwsEmptyCart() {
        cart(10L, 101L);
        assertThrows(EmptyCartException.class, () -> createOrder.execute(new CreateOrderCommand(10L, 101L)));
        assertEquals(0, orderStore.saves);
    }

    @Test
    void createOrder_whenProductInactive_throwsProductUnavailable() {
        cart(10L, 101L, new CartItemSnapshot(3L, 1, new BigDecimal("10")));
        assertThrows(ProductUnavailableException.class, () -> createOrder.execute(new CreateOrderCommand(10L, 101L)));
        assertEquals(0, orderStore.saves);
    }

    @Test
    void createOrder_whenStockNowInsufficient_throwsAndSavesNothing() {
        products.put(2L, new ProductSnapshot(2L, "Mouse", new BigDecimal("150000"), 1));
        cart(10L, 101L,
                new CartItemSnapshot(1L, 1, new BigDecimal("350000")),
                new CartItemSnapshot(2L, 2, new BigDecimal("300000")));

        assertThrows(InsufficientStockException.class, () -> createOrder.execute(new CreateOrderCommand(10L, 101L)));
        assertEquals(0, orderStore.saves);
    }

    @Test
    void createOrder_whenProductServiceDown_propagatesDependencyFailure() {
        cart(10L, 101L, new CartItemSnapshot(1L, 1, new BigDecimal("350000")));
        CreateOrderUseCase useCase = new CreateOrderUseCase(orderStore,
                cartId -> Optional.ofNullable(carts.get(cartId)),
                productId -> { throw new ProductServiceUnavailableException("down", null); });

        assertThrows(ProductServiceUnavailableException.class, () -> useCase.execute(new CreateOrderCommand(10L, 101L)));
        assertEquals(0, orderStore.saves);
    }

    @Test
    void getOrder_returnsExistingOrNotFound() {
        Order saved = orderStore.save(sampleOrder(OrderStatus.PENDING));
        var useCase = new GetOrderUseCase(orderStore);

        assertEquals(saved.getId(), useCase.execute(saved.getId()).getId());
        assertThrows(OrderNotFoundException.class, () -> useCase.execute(999L));
    }

    @Test
    void patchOrder_changesAddressWhilePending() {
        Order saved = orderStore.save(sampleOrder(OrderStatus.PENDING));
        var useCase = new PatchOrderUseCase(orderStore);

        Order patched = useCase.execute(new PatchOrderCommand(saved.getId(),
                PatchField.supplied("Jl. Veteran No. 10, Malang")));

        assertEquals("Jl. Veteran No. 10, Malang", patched.getShippingAddress());
        assertEquals("Jl. Veteran No. 10, Malang", orderStore.findById(saved.getId()).orElseThrow().getShippingAddress());
        assertEquals(saved.getTotal(), patched.getTotal());
    }

    @Test
    void patchOrder_rejectsEmptyNullAndUnknownOrder() {
        Order saved = orderStore.save(sampleOrder(OrderStatus.PENDING));
        var useCase = new PatchOrderUseCase(orderStore);

        assertThrows(DomainRuleViolation.class, () ->
                useCase.execute(new PatchOrderCommand(saved.getId(), PatchField.absent())));
        assertThrows(DomainRuleViolation.class, () ->
                useCase.execute(new PatchOrderCommand(saved.getId(), PatchField.supplied(null))));
        assertThrows(OrderNotFoundException.class, () ->
                useCase.execute(new PatchOrderCommand(999L, PatchField.supplied("Alamat"))));
    }

    @Test
    void patchOrder_whenNotPending_throwsConflictAndKeepsAddress() {
        Order saved = orderStore.save(sampleOrder(OrderStatus.CONFIRMED));
        int savesBefore = orderStore.saves;
        var useCase = new PatchOrderUseCase(orderStore);

        assertThrows(OrderNotModifiableException.class, () ->
                useCase.execute(new PatchOrderCommand(saved.getId(), PatchField.supplied("Alamat baru"))));
        assertEquals(savesBefore, orderStore.saves);
        assertNull(orderStore.findById(saved.getId()).orElseThrow().getShippingAddress());
    }

    private static Order sampleOrder(OrderStatus status) {
        Order placed = Order.place(101L, 10L, List.of(OrderItem.create(1L, "Keyboard", 1, new BigDecimal("350000"))));
        return status == OrderStatus.PENDING ? placed
                : Order.restore(1L, placed.getCustomerId(), placed.getCartId(), status, null, placed.getItems());
    }

    private static final class RecordingOrderStore implements OrderStore {
        private final Map<Long, Order> orders = new HashMap<>();
        private long nextId = 500;
        int saves;

        @Override
        public Order save(Order order) {
            saves++;
            Long id = order.getId() == null ? ++nextId : order.getId();
            Order stored = Order.restore(id, order.getCustomerId(), order.getCartId(), order.getStatus(),
                    order.getShippingAddress(), order.getItems());
            orders.put(id, stored);
            return stored;
        }

        @Override
        public Optional<Order> findById(Long orderId) {
            return Optional.ofNullable(orders.get(orderId));
        }
    }
}
