package id.ac.ub.abl.payment.adapter.inbound.rest;
import id.ac.ub.abl.payment.adapter.inbound.rest.mapper.PaymentHttpMapper;
import id.ac.ub.abl.payment.adapter.inbound.rest.simulator.*;
import id.ac.ub.abl.payment.usecase.service.ApplyPaymentResultUseCase;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class SimulatorActivationTest {
    @Mock ApplyPaymentResultUseCase apply;
    WebApplicationContextRunner runner(String profile, boolean enabled, String address, String token) {
        return new WebApplicationContextRunner().withUserConfiguration(Mvc.class, PaymentSimulatorConfiguration.class, DevelopmentPaymentResultController.class)
                .withBean(PaymentHttpMapper.class).withBean(ApplyPaymentResultUseCase.class, () -> apply)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile.equals("none") ? new String[0] : profile.split("\\+")))
                .withPropertyValues("payment.simulator.enabled=" + enabled, "server.address=" + address, "payment.simulator.token=" + token);
    }
    @ParameterizedTest @CsvSource({"none,true,false", "prod,true,false", "local,true,true", "test,true,true",
            "local,false,false", "test,false,false", "local+prod,true,false", "test+prod,true,false"})
    void activation_whenProfileAndFlagCombined_shouldRegisterOnlyAllowedRoutes(String profile, boolean enabled, boolean registered) {
        runner(profile, enabled, "127.0.0.1", PaymentsHttpBoundaryTest.TOKEN).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(DevelopmentPaymentResultController.class).isEmpty()).isEqualTo(!registered);
            if (!registered) {
                org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).build()
                        .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/dev/payments/9001/result")
                                .contentType("application/json").content("{\"status\":\"SUCCESS\"}"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
            }
        });
    }
    @ParameterizedTest @ValueSource(strings = {"", "0.0.0.0", "::", "192.0.2.1"})
    void startup_whenNotLoopback_shouldFail(String address) {
        runner("local", true, address, PaymentsHttpBoundaryTest.TOKEN).run(context -> assertThat(context).hasFailed());
    }
    @ParameterizedTest @ValueSource(strings = {"", "short", "1234567890123456789012345678901"})
    void startup_whenTokenInvalid_shouldFailWithoutLeakingToken(String token) {
        runner("test", true, "127.0.0.1", token).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure().getMessage()).doesNotContain("'" + token + "'");
        });
    }
    @ParameterizedTest @ValueSource(strings = {"127.0.0.1", "::1"})
    void startup_whenLoopback_shouldAcceptAndCompareTokenExactly(String address) {
        var access = new SimulatorAccess(address, PaymentsHttpBoundaryTest.TOKEN);
        assertThat(access.accepts(PaymentsHttpBoundaryTest.TOKEN)).isTrue();
        assertThat(access.accepts(PaymentsHttpBoundaryTest.TOKEN + " ")).isFalse();
        assertThat(access.accepts(null)).isFalse();
    }
    @TestConfiguration(proxyBeanMethods = false) @EnableWebMvc static class Mvc { }
}
