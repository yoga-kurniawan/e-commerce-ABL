package id.ac.ub.abl.order.adapter.outbound.database;

import java.util.Optional;

import id.ac.ub.abl.order.adapter.outbound.database.entity.OrderJpaEntity;
import id.ac.ub.abl.order.adapter.outbound.database.mapper.OrderPersistenceMapper;
import id.ac.ub.abl.order.domain.Order;
import id.ac.ub.abl.order.usecase.port.out.OrderStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Menghubungkan port aplikasi dengan repository JPA lokal. */
@Repository
@Transactional(readOnly = true)
public class OrderPersistenceAdapter implements OrderStore {
    private final OrdersRepository repository;
    private final OrderPersistenceMapper mapper;

    public OrderPersistenceAdapter(OrdersRepository repository, OrderPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Order dan seluruh item disimpan dalam satu transaksi lokal. */
    @Override
    @Transactional
    public Order save(Order order) {
        OrderJpaEntity entity = order.getId() == null
                ? mapper.toEntity(order)
                : repository.findById(order.getId())
                        .map(existing -> {
                            mapper.updateEntity(existing, order);
                            return existing;
                        })
                        .orElseGet(() -> mapper.toEntity(order));
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return repository.findById(orderId).map(mapper::toDomain);
    }
}
