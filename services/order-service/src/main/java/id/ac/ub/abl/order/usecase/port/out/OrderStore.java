package id.ac.ub.abl.order.usecase.port.out;

import java.util.Optional;

import id.ac.ub.abl.order.domain.Order;

/** Port penyimpanan lokal Order beserta item snapshot-nya. */
public interface OrderStore {
    /** Menyimpan pesanan baru atau perubahan atribut; hasil memuat ID dari penyimpanan. */
    Order save(Order order);

    Optional<Order> findById(Long orderId);
}
