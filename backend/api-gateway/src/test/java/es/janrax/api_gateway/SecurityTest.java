package es.janrax.api_gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayTestBase.TestRouteConfig.class)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
})
class SecurityTest extends GatewayTestBase {

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        setupMockJwksEndpoint();
        setupDefaultBackendStub();
    }

    @Test
    void graphqlPublicEndpointShouldNotRequireAuth() {
        webTestClient.get()
            .uri("/graphql")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void healthEndpointShouldNotRequireAuth() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void queueEndpointShouldRequireAuth() {
        webTestClient.get()
            .uri("/api/v1/queue/event-1/join")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void reservationEndpointShouldRequireAuth() {
        webTestClient.get()
            .uri("/api/v1/reservations")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void paymentEndpointShouldRequireAuth() {
        webTestClient.get()
            .uri("/api/v1/payments")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void validJwtShouldAccessProtectedEndpoint() {
        webTestClient.get()
            .uri("/api/v1/queue/event-1/join")
            .header("Authorization", "Bearer " + validToken())
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void invalidJwtShouldBeRejected() {
        webTestClient.get()
            .uri("/api/v1/queue/event-1/join")
            .header("Authorization", "Bearer " + expiredToken())
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
