package id.ac.ub.abl.order.domain.exception;

public class DomainRuleViolation extends RuntimeException {
    public DomainRuleViolation(String message) {
        super(message);
    }
}
