package id.ac.ub.abl.order.configuration;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import id.ac.ub.abl.order.adapter.outbound.rest.CartApiClient;
import id.ac.ub.abl.order.adapter.outbound.rest.PaymentApiClient;
import id.ac.ub.abl.order.adapter.outbound.rest.ProductApiClient;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.service.registry.ImportHttpServices;

/** Registrasi client deklaratif Cart/Product/Payment dan executor bounded untuk lookup produk paralel. */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "carts", types = CartApiClient.class)
@ImportHttpServices(group = "products", types = ProductApiClient.class)
@ImportHttpServices(group = "payments", types = PaymentApiClient.class)
public class RestClientConfiguration {

    @Bean(name = "productLookupExecutor")
    public ThreadPoolTaskExecutor productLookupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("product-lookup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(contextPropagatingDecorator());
        executor.initialize();
        return executor;
    }

    private static TaskDecorator contextPropagatingDecorator() {
        return runnable -> {
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            return () -> {
                RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
                Map<String, String> previousMdc = MDC.getCopyOfContextMap();
                try {
                    if (requestAttributes != null) {
                        RequestContextHolder.setRequestAttributes(requestAttributes);
                    } else {
                        RequestContextHolder.resetRequestAttributes();
                    }
                    if (mdcContext != null) {
                        MDC.setContextMap(mdcContext);
                    } else {
                        MDC.clear();
                    }
                    runnable.run();
                } finally {
                    if (previousAttributes != null) {
                        RequestContextHolder.setRequestAttributes(previousAttributes);
                    } else {
                        RequestContextHolder.resetRequestAttributes();
                    }
                    if (previousMdc != null) {
                        MDC.setContextMap(previousMdc);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
