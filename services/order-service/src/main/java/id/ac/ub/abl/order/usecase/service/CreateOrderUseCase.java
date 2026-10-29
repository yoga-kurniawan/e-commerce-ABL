package id.ac.ub.abl.order.usecase.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import id.ac.ub.abl.order.domain.CartItemSnapshot;
import id.ac.ub.abl.order.domain.CartSnapshot;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.domain.OrderItem;
import id.ac.ub.abl.order.domain.ProductSnapshot;
import id.ac.ub.abl.order.usecase.command.CreateOrderCommand;
import id.ac.ub.abl.order.usecase.exception.CartNotFoundException;
import id.ac.ub.abl.order.usecase.exception.CartOwnershipMismatchException;
import id.ac.ub.abl.order.usecase.exception.EmptyCartException;
import id.ac.ub.abl.order.usecase.exception.InsufficientStockException;
import id.ac.ub.abl.order.usecase.exception.ProductUnavailableException;
import id.ac.ub.abl.order.usecase.port.out.CartLookup;
import id.ac.ub.abl.order.usecase.port.out.OrderStore;
import id.ac.ub.abl.order.usecase.port.out.ProductLookup;
import org.springframework.stereotype.Service;

/**
 * Checkout: harga dari Cart (OPEN-003 B), nama dan stok dari Product.
 * Lookup HTTP dilakukan di luar transaksi; OrderStore.save menyimpan Order dan item secara atomik.
 */
@Service
public class CreateOrderUseCase {
    private final OrderStore orderStore;
    private final CartLookup cartLookup;
    private final ProductLookup productLookup;

    public CreateOrderUseCase(OrderStore orderStore, CartLookup cartLookup, ProductLookup productLookup) {
        this.orderStore = orderStore;
        this.cartLookup = cartLookup;
        this.productLookup = productLookup;
    }

    public Order execute(CreateOrderCommand command) {
        CartSnapshot cart = cartLookup.findById(command.cartId())
                .orElseThrow(() -> new CartNotFoundException(command.cartId()));
        if (!Objects.equals(cart.customerId(), command.customerId())) {
            throw new CartOwnershipMismatchException(command.cartId(), command.customerId());
        }
        if (cart.isEmpty()) {
            throw new EmptyCartException(command.cartId());
        }

        List<OrderItem> items = new ArrayList<>();
        for (CartItemSnapshot cartItem : cart.items()) {
            ProductSnapshot product = productLookup.findById(cartItem.productId())
                    .orElseThrow(() -> new ProductUnavailableException(cartItem.productId()));
            if (cartItem.quantity() > product.stock()) {
                throw new InsufficientStockException(cartItem.productId());
            }
            items.add(OrderItem.create(cartItem.productId(), product.name(),
                    cartItem.quantity(), cartItem.unitPrice()));
        }

        return orderStore.save(Order.place(command.customerId(), cart.id(), items));
    }
}
