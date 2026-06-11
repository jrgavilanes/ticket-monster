package es.janrax.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Value("${MONOLITH_URI:http://localhost:8082}")
    private String monolithUri;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("catalog-graphql", r -> r
                .path("/graphql")
                .filters(f -> f.circuitBreaker(config -> config
                    .setName("catalog")
                    .setFallbackUri("forward:/fallback/catalog")))
                .uri(monolithUri))
            .route("queue-service", r -> r
                .path("/api/v1/queue/**")
                .filters(f -> f.circuitBreaker(config -> config
                    .setName("queue")
                    .setFallbackUri("forward:/fallback/queue")))
                .uri(monolithUri))
            .route("reservation-service", r -> r
                .path("/api/v1/reservations/**")
                .filters(f -> f.circuitBreaker(config -> config
                    .setName("reservation")
                    .setFallbackUri("forward:/fallback/reservation")))
                .uri(monolithUri))
            .route("payment-service", r -> r
                .path("/api/v1/payments/**")
                .filters(f -> f.circuitBreaker(config -> config
                    .setName("payment")
                    .setFallbackUri("forward:/fallback/payment")))
                .uri(monolithUri))
            .build();
    }
}
