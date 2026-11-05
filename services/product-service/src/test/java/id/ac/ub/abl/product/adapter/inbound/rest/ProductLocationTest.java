package id.ac.ub.abl.product.adapter.inbound.rest;

import java.math.BigDecimal;
import java.net.URI;

import id.ac.ub.abl.product.adapter.inbound.rest.dto.ProductInput;
import id.ac.ub.abl.product.adapter.inbound.rest.mapper.ProductHttpMapper;
import id.ac.ub.abl.product.domain.Product;
import id.ac.ub.abl.product.usecase.service.CreateProductUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLocationTest {
    @Mock CreateProductUseCase create;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:18080/api/v1/products",
            "http://localhost:18081/api/v1/products",
            "https://shop.example/api/v1/products"
    })
    void create_whenProxyAddressDiffers_shouldResolveLocationAgainstClientOrigin(String externalUrl) {
        // Reproduce the backend's view: the public port is no longer present.
        var request = new MockHttpServletRequest("POST", "/api/v1/products");
        request.setServerName("127.0.0.1");
        request.setServerPort(80);
        request.setQueryString("debug=true");
        request.addHeader("X-Forwarded-Host", "untrusted.example");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(create.execute(any())).thenReturn(Product.restore(3L, "Mouse", new BigDecimal("150000"), 10));
        var controller = new ProductsApiController(create, null, null, null, null, null,
                new ProductHttpMapper());

        var response = controller.createProduct(new ProductInput("Mouse", new BigDecimal("150000"), 10));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        URI location = response.getHeaders().getLocation();
        assertThat(location).isEqualTo(URI.create("/api/v1/products/3"));
        assertThat(URI.create(externalUrl).resolve(location).toString()).isEqualTo(externalUrl + "/3");
    }
}
