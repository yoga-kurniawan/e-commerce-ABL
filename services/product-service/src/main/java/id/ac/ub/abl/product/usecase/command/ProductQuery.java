package id.ac.ub.abl.product.usecase.command;

import id.ac.ub.abl.product.domain.exception.DomainRuleViolation;

/** Optional pagination; null limit means return every matching active product. */
public record ProductQuery(String name, Integer page, Integer size) {
    public ProductQuery {
        name = name == null ? "" : name;
        if (name.length() > 255 || (page != null && page < 0)
                || (size != null && (size < 1 || size > 100))) {
            throw new DomainRuleViolation("Filter atau paginasi produk tidak valid");
        }
    }

    public Integer limit() { return page == null && size == null ? null : size == null ? 20 : size; }
    public long offset() { return page == null ? 0L : (long) page * limit(); }
}
