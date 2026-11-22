package id.ac.ub.abl.cart.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;

@Configuration(proxyBeanMethods = false)
public class JsonHardeningConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer strictJsonDuplicateDetection() {
        return builder -> builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }
}
