package id.ac.ub.abl.order.usecase.command;

/** Input aplikasi untuk membuat pesanan dari keranjang. */
public record CreateOrderCommand(Long cartId, Long customerId) {
}
