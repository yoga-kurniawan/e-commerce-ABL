package id.ac.ub.abl.payment.adapter.inbound.rest.dto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
public record PaymentReferencePatch(String transactionReference) {
    @JsonIgnore @AssertTrue(message = "Reference wajib memuat karakter non-whitespace")
    public boolean isReferenceValid() {
        return transactionReference != null && transactionReference.codePoints().anyMatch(c ->
                !Character.isWhitespace(c) && !Character.isSpaceChar(c));
    }
}
