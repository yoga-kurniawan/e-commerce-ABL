package id.ac.ub.abl.cart.configuration;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ProductClientConfiguration {

    @Value("${clients.product-url:http://127.0.0.1:8081}")
    private String productUrl;

    @Bean
    public RestClient productRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return builder
                .baseUrl(productUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
