package id.ac.ub.abl.payment.adapter.outbound.rest.mapper;

import id.ac.ub.abl.payment.domain.OrderSnapshot;
import id.ac.ub.abl.payment.adapter.outbound.rest.dto.OrderResponse;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderSnapshotMapper {
    // Parser lokal agar aturan strict upstream tidak mengubah JSON endpoint lain.
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    public OrderSnapshot fromJson(Long requestedId, String body) {
        try {
            if (body == null || body.isBlank()) { throw new IllegalArgumentException("Body Order kosong"); }
            var node = json.readTree(body);
            if (!node.isObject() || !node.hasNonNull("id") || !node.get("id").isIntegralNumber()
                    || !node.hasNonNull("total") || !node.get("total").isNumber()
                    || !node.hasNonNull("status") || !node.get("status").isTextual()) {
                throw new IllegalArgumentException("Tipe/field respons Order tidak valid");
            }
            var response = new OrderResponse(node.get("id").bigIntegerValue().longValueExact(),
                    node.get("total").decimalValue(), node.get("status").asString());
            if (!requestedId.equals(response.id())) { throw new IllegalArgumentException("ID Order tidak sesuai"); }
            return new OrderSnapshot(response.id(), response.total(), OrderSnapshot.Status.valueOf(response.status()));
        } catch (RuntimeException invalid) {
            throw new PaymentOperationException("ORDER_UPSTREAM_ERROR", "Respons Order tidak sesuai kontrak", invalid);
        }
    }
}
