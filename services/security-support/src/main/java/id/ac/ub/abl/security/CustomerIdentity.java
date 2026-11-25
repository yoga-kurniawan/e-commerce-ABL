package id.ac.ub.abl.security;

import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Identity comes exclusively from a verified JWT, never from a request header/body. */
public class CustomerIdentity {
    public boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
    public long customerId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                String claim = Objects.toString(jwt.getToken().getClaim("customer_id"), "");
                if (!claim.matches("[1-9][0-9]*")) { throw new IllegalArgumentException(); }
                return Long.parseLong(claim);
            } catch (RuntimeException invalid) {
                throw new AccessDeniedException("Identitas customer tidak tersedia");
            }
        }
        throw new AccessDeniedException("JWT wajib tersedia");
    }
    public boolean matches(Long owner) {
        return isAdmin() || (owner != null && owner.longValue() == customerId());
    }
}
