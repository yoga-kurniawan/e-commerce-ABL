package id.ac.ub.abl.payment.configuration;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

@Configuration(proxyBeanMethods = false)
public class CorrelationHeaderPropagationConfiguration {

    @Bean
    public RestClientHttpServiceGroupConfigurer correlationHeaderHttpServiceGroupConfigurer() {
        return groups -> groups.forEachClient((group, clientBuilder) ->
                clientBuilder.requestInterceptor(correlationHeaderInterceptor()));
    }

    @Bean
    public RestClientCustomizer correlationHeaderRestClientCustomizer() {
        return restClientBuilder -> restClientBuilder.requestInterceptor(correlationHeaderInterceptor());
    }

    private static ClientHttpRequestInterceptor correlationHeaderInterceptor() {
        return (request, body, execution) -> {
            String requestId = RequestIdContext.get();
            if (requestId != null && !requestId.isBlank() && request.getHeaders().getFirst(RequestIdContext.HEADER_NAME) == null) {
                request.getHeaders().set(RequestIdContext.HEADER_NAME, requestId);
            }
            return execution.execute(request, body);
        };
    }
}
