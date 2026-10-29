package id.ac.ub.abl.payment.usecase.port.out;

import id.ac.ub.abl.payment.domain.OrderSnapshot;
import id.ac.ub.abl.payment.usecase.model.CreatePaymentResult;
import id.ac.ub.abl.payment.usecase.model.IdempotencyClaim;

/**
 * Batas kebutuhan finalisasi atomik; implementasi transaksi berada pada tahap berikutnya.
 * Tidak menyediakan save Payment biasa yang terpisah dari penulisan hasil idempotency.
 */
public interface PaymentCreationCommitter {
    /**
     * Dalam satu transaksi lokal: lock klaim, cek token/lease dan kecocokan ID Order,
     * lock guard, periksa attempt PENDING/SUCCESS, buat Payment, encode snapshot,
     * lalu simpan COMPLETED+respons dengan TTL 24 jam. Semua commit atau rollback bersama.
     * Tidak melakukan lookup HTTP di dalam transaksi. Gunakan encoder murni.
     * Konflik/hasil commit ambigu bukan Created; laporkan kegagalan yang dapat dibedakan.
     * Rollback yang dikonfirmasi completion dilaporkan lewat PaymentCreationRolledBackException.
     * Kegagalan lain tidak memberi izin release; caller wajib rekonsiliasi melalui inspect.
     */
    CreatePaymentResult.Created commit(IdempotencyClaim claim, OrderSnapshot order);
}
