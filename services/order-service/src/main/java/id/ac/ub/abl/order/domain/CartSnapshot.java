package id.ac.ub.abl.order.domain;

import java.util.List;

import id.ac.ub.abl.order.domain.exception.DomainRuleViolation;

/** Data Cart yang dibutuhkan Order; dimiliki Order Service, bukan entity Cart Service. */
public record CartSnapshot(Long id, Long customerId, List<CartItemSnapshot> items) {
    public CartSnapshot {
        if (id == null) {
            throw new DomainRuleViolation("ID keranjang wajib diisi");
        }
        if (customerId == null) {
            throw new DomainRuleViolation("ID customer keranjang wajib diisi");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
