package id.ac.ub.abl.payment.usecase.port.out;

import java.util.Optional;
import id.ac.ub.abl.payment.domain.OrderSnapshot;

public interface OrderLookup {
    /** Empty hanya untuk Order tidak ada; timeout/5xx/respons rusak harus kegagalan berbeda. */
    Optional<OrderSnapshot> findById(Long orderId);
}
