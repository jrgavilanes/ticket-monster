package es.janrax.api_gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtConfigTest {

    private JwtConfig jwtConfig;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
    }

    @Test
    void shouldExtractRolesFromRealmAccess() {
        var jwt = jwt(
            Map.of("realm_access", Map.of("roles", List.of("admin", "user")))
        );

        var auth = jwtConfig.jwtAuthenticationConverter().convert(jwt).block();

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
            .hasSize(2)
            .anyMatch(a -> a.getAuthority().equals("ROLE_admin"))
            .anyMatch(a -> a.getAuthority().equals("ROLE_user"));
    }

    @Test
    void shouldReturnEmptyAuthoritiesWhenNoRealmAccess() {
        var jwt = jwt(Map.of());

        var auth = jwtConfig.jwtAuthenticationConverter().convert(jwt).block();

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void shouldReturnEmptyAuthoritiesWhenNoRoles() {
        var jwt = jwt(Map.of("realm_access", Map.of()));

        var auth = jwtConfig.jwtAuthenticationConverter().convert(jwt).block();

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token");
        claims.forEach(builder::claim);
        return builder
            .header("alg", "RS256")
            .header("typ", "JWT")
            .subject("user-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }
}
