package id.ac.ub.abl.cart.domain.exception;

public class DomainRuleViolation extends RuntimeException {
    public DomainRuleViolation(String message) {
        super(message);
    }
}
