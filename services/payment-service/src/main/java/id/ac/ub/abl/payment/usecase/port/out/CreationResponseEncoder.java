package id.ac.ub.abl.payment.usecase.port.out;

import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.usecase.model.StoredCreationResponse;

/** Serialisasi murni tanpa I/O; adapter HTTP menyediakan JSON sesuai OpenAPI. */
public interface CreationResponseEncoder {
    /** Input Payment create tersimpan/PENDING; output adalah teks awal yang disimpan dan dikirim. */
    StoredCreationResponse encode(Payment payment);
}
