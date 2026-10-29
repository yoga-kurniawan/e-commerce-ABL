package id.ac.ub.abl.payment.adapter.outbound.rest;

import java.net.http.HttpConnectTimeoutException;
import id.ac.ub.abl.payment.adapter.outbound.rest.mapper.OrderSnapshotMapper;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import static org.assertj.core.api.Assertions.*;

class OrderTransportFailureTest {
    @Test void lookup_whenTransportReportsConnectTimeout_shouldPreserveTimeoutClassification() {
        OrderApiClient transport = id -> { throw new ResourceAccessException("connect timeout", new HttpConnectTimeoutException("injected")); };
        var adapter = new OrderRestAdapter(transport, new OrderSnapshotMapper());
        assertThatThrownBy(() -> adapter.findById(501L)).isInstanceOfSatisfying(PaymentOperationException.class,
                error -> assertThat(error.getCode()).isEqualTo("ORDER_TIMEOUT"));
    }
}
