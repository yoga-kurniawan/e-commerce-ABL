package id.ac.ub.abl.payment.usecase.port.out;

import id.ac.ub.abl.payment.usecase.command.CreatePaymentCommand;
import id.ac.ub.abl.payment.usecase.model.IdempotencyClaim;
import id.ac.ub.abl.payment.usecase.model.IdempotencyClaimResult;
import id.ac.ub.abl.payment.usecase.model.IdempotencyLookupResult;

public interface IdempotencyStore {
    /**
     * Satu transaksi singkat: klaim/takeover/replay/konflik di scope IdempotencyKey.OPERATION.
     * Takeover lease usang wajib atomik dan mengganti owner token; bukan check-then-insert.
     * Clock, expiry, lock budget, dan fencing dikelola implementasi database.
     */
    IdempotencyClaimResult claim(CreatePaymentCommand request);

    /**
     * Inspeksi tanpa mutasi pada koneksi/transaksi baru setelah hasil commit belum jelas.
     * Database/lock yang tidak dapat dibaca harus melempar kegagalan, bukan Missing.
     * Missing/Processing tidak memberi izin menghapus klaim atau menganggap rollback.
     */
    IdempotencyLookupResult inspect(CreatePaymentCommand request);

    /**
     * Hanya dipanggil setelah rollback dipastikan. Hapus PROCESSING iff owner token cocok;
     * false jika state/pemilik berubah atau klaim tidak ada. Jangan menghapus COMPLETED.
     */
    boolean releaseAfterConfirmedRollback(IdempotencyClaim claim);

    /** Cleanup COMPLETED expired saja, tidak menghapus Payment atau PROCESSING. */
    int deleteExpiredCompleted(int batchSize);
}
