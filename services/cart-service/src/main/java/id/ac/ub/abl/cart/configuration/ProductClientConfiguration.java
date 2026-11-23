package id.ac.ub.abl.cart.configuration;

import id.ac.ub.abl.cart.adapter.outbound.rest.ProductApiClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/** Registrasi client deklaratif group products; URL dan timeout diatur via spring.http.serviceclient.products.*. */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "products", types = ProductApiClient.class)
public class ProductClientConfiguration {
}
