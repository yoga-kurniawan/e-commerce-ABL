package id.ac.ub.abl.security;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
public class JwtSecurityConfiguration {
    private static final String VALIDATED_TOKEN = JwtSecurityConfiguration.class.getName() + ".token";

    @Bean(name = "identity") CustomerIdentity customerIdentity() { return new CustomerIdentity(); }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Enabled {
        @Bean JwtDecoder jwtDecoder(Environment env) {
            String issuer = env.getProperty("app.security.issuer", "http://localhost:18090/realms/ecommerce");
            String jwks = env.getProperty("app.security.jwk-set-uri", issuer + "/protocol/openid-connect/certs");
            String audience = env.getProperty("app.security.audience", "ecommerce-api");
            var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
            decoder.setJwtValidator(validators(issuer, audience));
            return decoder;
        }
        @Bean SecurityFilterChain authenticated(HttpSecurity http) throws Exception {
            var converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(jwt -> {
                Object access = jwt.getClaim("realm_access");
                Object roles = access instanceof Map<?, ?> map ? map.get("roles") : null;
                if (!(roles instanceof Collection<?> values)) { return List.of(); }
                return values.stream().filter(r -> "ADMIN".equals(r) || "CUSTOMER".equals(r))
                        .<org.springframework.security.core.GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
            });
            http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**", "/api/v2/products", "/api/v2/products/**").hasAnyRole("CUSTOMER", "ADMIN")
                    .requestMatchers("/api/v1/products", "/api/v1/products/**", "/api/v2/products", "/api/v2/products/**").hasRole("ADMIN")
                    .requestMatchers("/api/**").hasAnyRole("CUSTOMER", "ADMIN")
                    .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint((req, res, ex) -> error(res, 401))
                    .accessDeniedHandler((req, res, ex) -> error(res, 403)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((req, res, ex) -> error(res, 401))
                    .accessDeniedHandler((req, res, ex) -> error(res, 403)))
                .addFilterAfter(new OncePerRequestFilter() {
                    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                            FilterChain chain) throws ServletException, IOException {
                        var auth = SecurityContextHolder.getContext().getAuthentication();
                        if (auth instanceof JwtAuthenticationToken jwt) {
                            request.setAttribute(VALIDATED_TOKEN, jwt.getToken().getTokenValue());
                        }
                        try { chain.doFilter(request, response); }
                        finally { request.removeAttribute(VALIDATED_TOKEN); }
                    }
                }, BearerTokenAuthenticationFilter.class);
            return http.build();
        }
        @Bean RestClientHttpServiceGroupConfigurer bearerRelay() {
            // Only declarative internal HTTP-service clients; do not relay to arbitrary URLs.
            return groups -> groups.forEachClient((group, builder) -> builder.requestInterceptor((request, body, execution) -> {
                var attributes = RequestContextHolder.getRequestAttributes();
                Object token = attributes == null ? null : attributes.getAttribute(VALIDATED_TOKEN, RequestAttributes.SCOPE_REQUEST);
                if (token instanceof String value) { request.getHeaders().setBearerAuth(value); }
                return execution.execute(request, body);
            }));
        }
    }

    static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            // Require exp/sub as well as checking expiration, issuer, nbf, signature and audience.
            if (jwt.getExpiresAt() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || !jwt.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
            return OAuth2TokenValidatorResult.success();
        };
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), claims);
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
    SecurityFilterChain explicitlyDisabled(HttpSecurity http) throws Exception {
        // Only for isolated legacy business tests / explicitly selected local diagnostics.
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }

    private static void error(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        if (status == 401) { response.setHeader("WWW-Authenticate", "Bearer"); }
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + HttpStatus.valueOf(status).name()
                + "\",\"message\":\"Akses tidak diizinkan\"}");
    }
}
