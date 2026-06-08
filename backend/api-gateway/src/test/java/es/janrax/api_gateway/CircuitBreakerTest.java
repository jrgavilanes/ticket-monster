package es.janrax.api_gateway;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CircuitBreakerTest.CircuitBreakerRouteConfig.class)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "resilience4j.circuitbreaker.instances.catalog.sliding-window-size=3",
    "resilience4j.circuitbreaker.instances.catalog.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.catalog.wait-duration-in-open-state=60s",
    "resilience4j.circuitbreaker.instances.catalog.permitted-number-of-calls-in-half-open-state=1",
    "resilience4j.timelimiter.instances.catalog.timeout-duration=2s"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CircuitBreakerTest extends GatewayTestBase {

    @TestConfiguration
    static class CircuitBreakerRouteConfig {
        @Bean
        RouteLocator circuitBreakerRoutes(RouteLocatorBuilder builder) {
            return builder.routes()
                .route("catalog-graphql", r -> r
                    .path("/graphql")
                    .filters(f -> f.circuitBreaker(cb -> cb
                        .setName("catalog")
                        .setFallbackUri("forward:/fallback/catalog")
                        .addStatusCode("INTERNAL_SERVER_ERROR")))
                    .uri(wireMock.baseUrl()))
                .build();
        }
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        setupDefaultBackendStub();
    }

    @Test
    void shouldReturn503WhenCircuitBreakerOpens() {
        wireMock.stubFor(WireMock.any(urlPathMatching("/graphql"))
            .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 3; i++) {
            webTestClient.get()
                .uri("/graphql")
                .exchange()
                .expectStatus().is5xxServerError();
        }

        var result = webTestClient.get()
            .uri("/graphql")
            .exchange()
            .expectStatus().is5xxServerError()
            .expectBody()
            .jsonPath("$.error").isEqualTo("Catalog service is temporarily unavailable")
            .jsonPath("$.status").isEqualTo(503)
            .returnResult();

        assertThat(result.getResponseBody()).isNotNull();
    }

    @Test
    void catalogFallbackShouldReturnModuleSpecificMessage() {
        wireMock.stubFor(WireMock.any(urlPathMatching("/graphql"))
            .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 3; i++) {
            webTestClient.get()
                .uri("/graphql")
                .exchange()
                .expectStatus().is5xxServerError();
        }

        webTestClient.get()
            .uri("/graphql")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Catalog service is temporarily unavailable")
            .jsonPath("$.status").isEqualTo(503);
    }

    @Test
    void shouldOpenCircuitBreakerOnConnectionTimeout() {
        wireMock.stubFor(WireMock.any(urlPathMatching("/graphql"))
            .willReturn(aResponse()
                .withFixedDelay(5000)));

        for (int i = 0; i < 3; i++) {
            webTestClient.get()
                .uri("/graphql")
                .exchange()
                .expectStatus().is5xxServerError();
        }

        webTestClient.get()
            .uri("/graphql")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Catalog service is temporarily unavailable");
    }
}
