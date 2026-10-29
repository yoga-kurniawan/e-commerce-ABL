package id.ac.ub.abl.payment.adapter.inbound.rest.simulator;
import java.util.Collections;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
@Configuration(proxyBeanMethods = false)
@Profile("(local | test) & !prod")
@ConditionalOnProperty(name = "payment.simulator.enabled", havingValue = "true")
public class PaymentSimulatorConfiguration implements WebMvcConfigurer {
    private final SimulatorAccess access;
    public PaymentSimulatorConfiguration(Environment environment) {
        access = new SimulatorAccess(environment.getProperty("server.address"),
                environment.getProperty("payment.simulator.token", environment.getProperty("PAYMENT_SIMULATOR_TOKEN", "")));
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                var tokens = Collections.list(request.getHeaders("X-Simulator-Token"));
                if (tokens.size() == 1 && access.accepts(tokens.get(0))) { return true; }
                response.setStatus(401); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Token simulator tidak valid\"}");
                return false;
            }
        }).addPathPatterns("/internal/dev/payments/*/result");
    }
}
