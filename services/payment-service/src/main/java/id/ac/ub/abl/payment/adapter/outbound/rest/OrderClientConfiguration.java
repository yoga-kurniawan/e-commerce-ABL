package id.ac.ub.abl.payment.adapter.outbound.rest;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "orders", types = OrderApiClient.class)
public class OrderClientConfiguration { }
