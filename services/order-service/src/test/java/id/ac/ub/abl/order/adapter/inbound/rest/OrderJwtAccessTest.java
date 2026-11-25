package id.ac.ub.abl.order.adapter.inbound.rest;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import id.ac.ub.abl.security.JwtSecurityConfiguration;
import id.ac.ub.abl.order.adapter.inbound.rest.mapper.OrderHttpMapper;
import id.ac.ub.abl.order.configuration.OrderAccess;
import id.ac.ub.abl.order.domain.*;
import id.ac.ub.abl.order.usecase.service.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({OrdersApiController.class,OrdersApiV2Controller.class})
@Import({JwtSecurityConfiguration.class,OrderAccess.class,OrderHttpMapper.class})
@TestPropertySource(properties="app.security.enabled=true")
class OrderJwtAccessTest {
 @Autowired MockMvc http;
 @MockitoBean JwtDecoder decoder;
 private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customer() {
  return jwt().jwt(j -> j.subject("user-a").claim("customer_id","101")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
 }

 @MockitoBean CreateOrderUseCase create;
 @MockitoBean GetOrderUseCase get;
 @MockitoBean PatchOrderUseCase patch;
 @MockitoBean ListOrdersUseCase list;
 @MockitoBean CancelOrderUseCase cancel;
 @BeforeEach void setup() {
  Order order=mock(Order.class); when(order.getCustomerId()).thenReturn(202L); when(get.execute(9L)).thenReturn(order);
 }
 @ParameterizedTest @ValueSource(strings={"v1","v2"})
 void otherCustomerCannotReadOrMutateOrder(String version) throws Exception {
  String p="/api/"+version+"/orders/9";
  http.perform(get(p).with(customer())).andExpect(status().isForbidden());
  http.perform(patch(p).with(customer()).contentType("application/json").content("{\"shippingAddress\":\"Other\"}")).andExpect(status().isForbidden());
  if(version.equals("v2")) http.perform(post(p+"/cancel").with(customer())).andExpect(status().isForbidden());
  verifyNoInteractions(patch,cancel);
 }
 @ParameterizedTest @ValueSource(strings={"v1","v2"})
 void spoofedCheckoutCustomerIsRejected(String version) throws Exception {
  http.perform(post("/api/"+version+"/orders").with(customer()).contentType("application/json").content("{\"cartId\":9,\"customerId\":202}")).andExpect(status().isForbidden());
  verifyNoInteractions(create);
 }
 @Test void listIsScopedToCustomer() throws Exception {
  http.perform(get("/api/v2/orders?customerId=202").with(customer())).andExpect(status().isForbidden());
  verifyNoInteractions(list);
  when(list.execute(101L)).thenReturn(List.of());
  http.perform(get("/api/v2/orders?customerId=101").with(customer())).andExpect(status().isOk());
 }
}
