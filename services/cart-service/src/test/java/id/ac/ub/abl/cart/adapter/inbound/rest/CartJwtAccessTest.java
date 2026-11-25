package id.ac.ub.abl.cart.adapter.inbound.rest;
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
import id.ac.ub.abl.cart.adapter.inbound.rest.mapper.CartHttpMapper;
import id.ac.ub.abl.cart.configuration.CartAccess;
import id.ac.ub.abl.cart.domain.*;
import id.ac.ub.abl.cart.usecase.service.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({CartsApiController.class,CartsApiV2Controller.class})
@Import({JwtSecurityConfiguration.class,CartAccess.class,CartHttpMapper.class})
@TestPropertySource(properties="app.security.enabled=true")
class CartJwtAccessTest {
 @Autowired MockMvc http;
 @MockitoBean JwtDecoder decoder;
 private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customer() {
  return jwt().jwt(j -> j.subject("user-a").claim("customer_id","101")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
 }

 @MockitoBean CreateCartUseCase create;
 @MockitoBean GetCartUseCase get;
 @MockitoBean AddCartItemUseCase add;
 @MockitoBean ReplaceCartItemUseCase replace;
 @MockitoBean PatchCartItemUseCase patch;
 @MockitoBean DeleteCartItemUseCase delete;
 @MockitoBean ClearCartItemsUseCase clear;
 @BeforeEach void setup() { when(get.execute(9L)).thenReturn(Cart.restore(9L,202L,List.of())); }
 @ParameterizedTest @ValueSource(strings={"v1","v2"})
 void otherCustomerCannotReadOrMutateCart(String version) throws Exception {
  String p="/api/"+version+"/carts/9";
  http.perform(get(p).with(customer())).andExpect(status().isForbidden());
  http.perform(post(p+"/items").with(customer()).contentType("application/json").content("{\"productId\":1,\"quantity\":1}")).andExpect(status().isForbidden());
  http.perform(put(p+"/items/1").with(customer()).contentType("application/json").content("{\"quantity\":1}")).andExpect(status().isForbidden());
  http.perform(patch(p+"/items/1").with(customer()).contentType("application/json").content("{\"quantity\":1}")).andExpect(status().isForbidden());
  http.perform(delete(p+"/items/1").with(customer())).andExpect(status().isForbidden());
  if(version.equals("v2")) http.perform(delete(p+"/items").with(customer())).andExpect(status().isForbidden());
  verifyNoInteractions(add,replace,patch,delete,clear);
 }
 @ParameterizedTest @ValueSource(strings={"v1","v2"})
 void customerCannotSpoofCreateOwner(String version) throws Exception {
  http.perform(post("/api/"+version+"/carts").with(customer()).contentType("application/json").content("{\"customerId\":202}")).andExpect(status().isForbidden());
  verifyNoInteractions(create);
 }
 @Test void ownerCanRead() throws Exception {
  when(get.execute(9L)).thenReturn(Cart.restore(9L,101L,List.of()));
  http.perform(get("/api/v2/carts/9").with(customer())).andExpect(status().isOk());
 }
}
