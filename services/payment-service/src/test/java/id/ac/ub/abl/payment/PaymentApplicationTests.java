package id.ac.ub.abl.payment;

import org.junit.jupiter.api.Test;
import id.ac.ub.abl.payment.integration.PaymentIntegrationSupport;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "payment.simulator.enabled=false")
class PaymentApplicationTests extends PaymentIntegrationSupport {

	@Test
	void application_whenSimulatorDisabled_shouldServePaymentAndHideSimulator() {
		String created = create("disabled-simulator", 501, "COD", 201);
		long id = id(created);
		assertThat(request("GET", BASE + "/" + id, null, null, false, 200)).isEqualTo(created);
		error(result(id, "SUCCESS", 404), "NOT_FOUND");
		assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, id)).isEqualTo("PENDING");
	}

	@Test
	void actuator_healthReadinessAndPrometheusReturnOk() {
		assertThat(request("GET", "/actuator/health/readiness", null, null, false, 200)).contains("UP");
		assertThat(request("GET", "/actuator/prometheus", null, null, false, 200)).contains("jvm_");
	}

	@Test
	void json_duplicateKeysAreRejectedWithBadRequest() {
		String duplicatePayload = "{\"orderId\": 502, \"orderId\": 503, \"method\": \"COD\"}";
		error(request("POST", BASE, duplicatePayload, "idemp-dupe", false, 400), "BAD_REQUEST");
	}

	@Test
	void outbound_propagatesCorrelationHeaderAndTracing() {
		orders().reset();
		requestWithHeaders("POST", BASE, "{\"orderId\": 504, \"method\": \"COD\"}",
				"idemp-prop", false, java.util.Map.of("x-request-id", "pay-req-789"), 201);

		var headers = orders().requestHeaders.poll();
		assertThat(headers).isNotNull();
		assertThat(headers.getFirst("x-request-id")).isEqualTo("pay-req-789");
		assertThat(headers.getFirst("traceparent")).isNotBlank();
	}

}
