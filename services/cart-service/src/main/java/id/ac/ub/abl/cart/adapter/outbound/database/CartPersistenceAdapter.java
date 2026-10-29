package id.ac.ub.abl.cart.adapter.outbound.database;

import java.util.Optional;

import id.ac.ub.abl.cart.adapter.outbound.database.entity.CartJpaEntity;
import id.ac.ub.abl.cart.adapter.outbound.database.mapper.CartPersistenceMapper;
import id.ac.ub.abl.cart.domain.Cart;
import id.ac.ub.abl.cart.usecase.port.out.CartStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Menghubungkan port aplikasi dengan repository JPA lokal. */
@Repository
@Transactional(readOnly = true)
public class CartPersistenceAdapter implements CartStore {
    private final CartsRepository repository;
    private final CartPersistenceMapper mapper;

    public CartPersistenceAdapter(CartsRepository repository, CartPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Cart save(Cart cart) {
        CartJpaEntity entity;
        if (cart.getId() != null) {
            Optional<CartJpaEntity> existing = repository.findById(cart.getId());
            if (existing.isPresent()) {
                entity = existing.get();
                mapper.updateEntity(entity, cart);
            } else {
                entity = mapper.toEntity(cart);
            }
        } else {
            entity = mapper.toEntity(cart);
        }
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Cart> findById(Long cartId) {
        return repository.findById(cartId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void delete(Cart cart) {
        if (cart.getId() != null) {
            repository.deleteById(cart.getId());
        }
    }
}
