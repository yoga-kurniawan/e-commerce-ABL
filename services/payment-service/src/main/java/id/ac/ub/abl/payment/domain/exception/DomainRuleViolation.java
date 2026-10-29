package id.ac.ub.abl.payment.domain.exception;

public class DomainRuleViolation extends RuntimeException {
    public DomainRuleViolation(String message) {
        super(message);
    }
}
