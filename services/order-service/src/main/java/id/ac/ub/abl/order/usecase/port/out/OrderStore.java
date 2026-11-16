package id.ac.ub.abl.order.usecase.port.out;

import java.util.List;
import java.util.Optional;

import id.ac.ub.abl.order.domain.Order;

/** Port penyimpanan lokal Order beserta item snapshot-nya. */
public interface OrderStore {
    /** Menyimpan pesanan baru atau perubahan atribut; hasil memuat ID dari penyimpanan. */
    Order save(Order order);

    Optional<Order> findById(Long orderId);

    /** Seluruh pesanan untuk customerId, diurutkan dari ID terbesar; list kosong jika tidak ada. */
    List<Order> findByCustomerId(Long customerId);
}
