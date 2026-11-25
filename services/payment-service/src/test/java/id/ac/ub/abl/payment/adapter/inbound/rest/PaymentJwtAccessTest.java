package id.ac.ub.abl.payment.adapter.inbound.rest;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
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
import id.ac.ub.abl.payment.adapter.inbound.rest.mapper.PaymentHttpMapper;
import id.ac.ub.abl.payment.configuration.PaymentAccess;
import id.ac.ub.abl.payment.domain.*;
import id.ac.ub.abl.payment.usecase.service.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({PaymentsApiController.class,PaymentsApiV2Controller.class})
@Import({JwtSecurityConfiguration.class,PaymentAccess.class,PaymentHttpMapper.class})
@TestPropertySource(properties="app.security.enabled=true")
class PaymentJwtAccessTest {
 @Autowired MockMvc http;
 @MockitoBean JwtDecoder decoder;
 private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customer() {
  return jwt().jwt(j -> j.subject("user-a").claim("customer_id","101")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
 }

 @MockitoBean CreatePaymentUseCase create;
 @MockitoBean GetPaymentUseCase get;
 @MockitoBean PatchPaymentUseCase patch;
 @MockitoBean ListPaymentsUseCase list;
 @MockitoBean id.ac.ub.abl.payment.usecase.port.out.OrderLookup orders;
 @BeforeEach void setup() {
  Payment payment=mock(Payment.class); when(payment.getOrderId()).thenReturn(9L); when(get.execute(7L)).thenReturn(payment);
  when(orders.findById(9L)).thenThrow(new org.springframework.security.access.AccessDeniedException("Not owner"));
 }
 @ParameterizedTest @ValueSource(strings={"v1","v2"})
 void otherCustomerCannotReadPatchOrReplayPayment(String version) throws Exception {
  String p="/api/"+version+"/payments";
  http.perform(get(p+"/7").with(customer())).andExpect(status().isForbidden());
  http.perform(patch(p+"/7").with(customer()).contentType("application/json").content("{\"transactionReference\":\"REF-1\"}")).andExpect(status().isForbidden());
  http.perform(post(p).with(customer()).header("Idempotency-Key","replay-key").contentType("application/json").content("{\"orderId\":9,\"method\":\"COD\"}")).andExpect(status().isForbidden());
  verifyNoInteractions(create,patch);
 }
 @Test void listChecksOrderOwnership() throws Exception {
  http.perform(get("/api/v2/payments?orderId=9").with(customer())).andExpect(status().isForbidden());
  verifyNoInteractions(list);
 }
}
