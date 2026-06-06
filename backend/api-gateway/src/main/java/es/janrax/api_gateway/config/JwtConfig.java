package es.janrax.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class JwtConfig {

	@Bean
	public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
		return jwt -> {
			Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
			return Mono.just(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
		};
	}

	@SuppressWarnings("unchecked")
	private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
		if (realmAccess == null) return List.of();

		List<String> roles = (List<String>) realmAccess.get("roles");
		if (roles == null) return List.of();

		return roles.stream()
				.map(role -> new SimpleGrantedAuthority("ROLE_" + role))
				.collect(Collectors.toList());
	}
}
