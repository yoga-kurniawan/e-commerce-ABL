package id.ac.ub.abl.order.usecase.port.out;

import java.util.List;

import id.ac.ub.abl.order.domain.PaymentSnapshot;

/** Port pembacaan riwayat Payment dari Payment Service (GET /api/v2/payments?orderId=). */
public interface PaymentLookup {
    /** List kosong bila Order belum punya Payment; kegagalan koneksi/respons invalid dilaporkan sebagai exception. */
    List<PaymentSnapshot> findByOrderId(Long orderId);
}
