package id.ac.ub.abl.order.configuration;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Client HTTP ke Cart dan Product; URL dari property clients.* yang diatur helper/Helm. */
@Configuration
public class RestClientConfiguration {

    @Bean
    public RestClient cartRestClient(RestClient.Builder builder,
                                     @Value("${clients.cart-url:http://127.0.0.1:8082}") String cartUrl) {
        return build(builder, cartUrl);
    }

    @Bean
    public RestClient productRestClient(RestClient.Builder builder,
                                        @Value("${clients.product-url:http://127.0.0.1:8081}") String productUrl) {
        return build(builder, productUrl);
    }

    private static RestClient build(RestClient.Builder builder, String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
