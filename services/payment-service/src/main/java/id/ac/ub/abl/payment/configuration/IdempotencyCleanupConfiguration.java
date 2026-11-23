package id.ac.ub.abl.payment.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "payment.idempotency.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyCleanupConfiguration {
}
