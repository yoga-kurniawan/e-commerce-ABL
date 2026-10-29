package id.ac.ub.abl.payment.adapter.outbound.rest;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.Objects;
import id.ac.ub.abl.payment.domain.OrderSnapshot;
import id.ac.ub.abl.payment.adapter.outbound.rest.mapper.OrderSnapshotMapper;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import id.ac.ub.abl.payment.usecase.port.out.OrderLookup;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OrderRestAdapter implements OrderLookup {
    private final OrderApiClient client;
    private final OrderSnapshotMapper mapper;
    public OrderRestAdapter(OrderApiClient client, OrderSnapshotMapper mapper) { this.client = client; this.mapper = mapper; }
    public Optional<OrderSnapshot> findById(Long orderId) {
        Objects.requireNonNull(orderId, "orderId");
        try {
            var response = client.get(orderId);
            var type = response.getHeaders().getContentType();
            if (response.getStatusCode().value() != 200 || type == null || !"application".equalsIgnoreCase(type.getType())
                    || !("json".equalsIgnoreCase(type.getSubtype()) || type.getSubtype().endsWith("+json"))) {
                throw new PaymentOperationException("ORDER_UPSTREAM_ERROR", "Status/content type Order tidak sesuai kontrak");
            }
            return Optional.of(mapper.fromJson(orderId, response.getBody()));
        } catch (org.springframework.http.InvalidMediaTypeException error) {
            throw new PaymentOperationException("ORDER_UPSTREAM_ERROR", "Content type Order tidak valid", error);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) { return Optional.empty(); }
            throw new PaymentOperationException("ORDER_UPSTREAM_ERROR", "Order Service mengembalikan respons gagal", error);
        } catch (ResourceAccessException error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                    throw new PaymentOperationException("ORDER_TIMEOUT", "Koneksi atau pembacaan Order melewati timeout", error);
                }
            }
            throw new PaymentOperationException("DEPENDENCY_UNAVAILABLE", "Tidak dapat terhubung ke Order Service", error);
        } catch (RestClientException error) {
            throw new PaymentOperationException("ORDER_UPSTREAM_ERROR", "Respons Order tidak dapat dibaca", error);
        }
    }
}
