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

}
