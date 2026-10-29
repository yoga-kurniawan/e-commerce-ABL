package id.ac.ub.abl.payment.usecase.command;

import id.ac.ub.abl.payment.usecase.exception.InvalidPaymentCommandException;

/** Field presence/unknown fields ditangani boundary; command hanya membawa reference valid. */
public record PatchPaymentCommand(Long paymentId, String transactionReference) {
    public PatchPaymentCommand {
        if (paymentId == null || transactionReference == null || transactionReference.codePoints()
                .noneMatch(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c))) {
            throw new InvalidPaymentCommandException("ID Payment dan reference nonblank wajib diisi");
        }
    }
}
