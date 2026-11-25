package id.ac.ub.abl.security;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig({JwtSecurityConfiguration.class, JwtSecurityTest.Web.class})
@WebAppConfiguration
@TestPropertySource(properties = "app.security.enabled=true")
class JwtSecurityTest {
    static final RSAKey KEY;
    static { try { KEY = new RSAKeyGenerator(2048).generate(); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @Autowired WebApplicationContext context;
    MockMvc http;
    @BeforeEach void setup() { http = webAppContextSetup(context).apply(springSecurity()).build(); }
    @Configuration @EnableWebMvc static class Web {
        @Bean Demo demo() { return new Demo(); }
        @Bean @Primary JwtDecoder testDecoder() throws Exception {
            var decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtSecurityConfiguration.validators("https://issuer.test", "ecommerce-api"));
            return decoder;
        }
    }
    @RestController static class Demo {
        @GetMapping("/api/v2/carts/{id}") @PreAuthorize("@identity.matches(#id)")
        public Map<String, Long> cart(@PathVariable Long id) { return Map.of("customerId", id); }
        @PostMapping("/api/v2/products") public String product() { return "ok"; }
        @GetMapping("/actuator/health/readiness") public String health() { return "ok"; }
    }
    static String token(RSAKey key, String issuer, String audience, Instant exp, boolean subject, String customer, String role) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience)
                .issueTime(new Date()).expirationTime(Date.from(exp)).claim("customer_id", customer)
                .claim("realm_access", Map.of("roles", List.of(role)));
        if (subject) { claims.subject("account-101"); }
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(key)); return jwt.serialize();
    }
    static String valid(String customer, String role) throws Exception {
        return token(KEY,"https://issuer.test","ecommerce-api",Instant.now().plusSeconds(600),true,customer,role);
    }
    @Test void missingTokenFailsAndHealthStaysOpen() throws Exception {
        http.perform(get("/api/v2/carts/101")).andExpect(status().isUnauthorized());
        http.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"expired","issuer","audience","signature","subject","malformed"})
    void invalidTokensAreRejected(String type) throws Exception {
        var signing = type.equals("signature") ? new RSAKeyGenerator(2048).generate() : KEY;
        String value = token(signing, type.equals("issuer")?"https://wrong.test":"https://issuer.test",
                type.equals("audience")?"other-api":"ecommerce-api",
                Instant.now().plusSeconds(type.equals("expired")?-600:600),!type.equals("subject"),"101","CUSTOMER");
        if (type.equals("malformed")) { value="not.a.jwt"; }
        http.perform(get("/api/v2/carts/101").header("Authorization","Bearer "+value)).andExpect(status().isUnauthorized());
    }
    @Test void signedIdentityAllowsOwnDataAndRejectsOtherCustomer() throws Exception {
        String value=valid("101","CUSTOMER");
        http.perform(get("/api/v2/carts/101").header("Authorization","Bearer "+value)).andExpect(status().isOk());
        http.perform(get("/api/v2/carts/202").header("Authorization","Bearer "+value)).andExpect(status().isForbidden());
        http.perform(get("/api/v2/carts/101").header("Authorization","Bearer "+valid("bad","CUSTOMER"))).andExpect(status().isForbidden());
    }
    @Test void customersCannotWriteProductsButAdminsCan() throws Exception {
        http.perform(post("/api/v2/products").header("Authorization","Bearer "+valid("101","CUSTOMER"))).andExpect(status().isForbidden());
        http.perform(post("/api/v2/products").header("Authorization","Bearer "+valid(null,"ADMIN"))).andExpect(status().isOk());
        http.perform(post("/internal/dev/payments/1/result").header("Authorization","Bearer "+valid(null,"ADMIN"))).andExpect(status().isForbidden());
    }
}
