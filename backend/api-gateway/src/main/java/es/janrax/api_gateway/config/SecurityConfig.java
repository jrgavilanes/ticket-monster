package es.janrax.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers("/graphql").permitAll()
						.pathMatchers("/actuator/**").permitAll()
						.pathMatchers("/fallback/**").permitAll()
						.pathMatchers("/api/v1/queue/**").authenticated()
						.pathMatchers("/api/v1/reservations/**").authenticated()
						.pathMatchers("/api/v1/payments/**").authenticated()
						.anyExchange().permitAll()
				)
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
				.build();
	}
}
