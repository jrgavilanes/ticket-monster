package es.janrax.api_gateway;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayTestBase.TestRouteConfig.class)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
})
class RoutingTest extends GatewayTestBase {

    private static Stream<Arguments> routePaths() {
        return Stream.of(
            Arguments.of("/graphql", "/graphql", false),
            Arguments.of("/api/v1/queue/event-123/join", "/api/v1/queue/event-123/join", true),
            Arguments.of("/api/v1/reservations", "/api/v1/reservations", true),
            Arguments.of("/api/v1/payments", "/api/v1/payments", true)
        );
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        setupMockJwksEndpoint();
        setupDefaultBackendStub();
    }

    @ParameterizedTest
    @MethodSource("routePaths")
    void shouldForwardToBackend(String gatewayPath, String backendPath, boolean requiresAuth) {
        wireMock.stubFor(WireMock.any(urlPathEqualTo(backendPath))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"ok\"}")));

        var spec = webTestClient.get().uri(gatewayPath);
        if (requiresAuth) {
            spec = spec.header("Authorization", "Bearer " + validToken());
        }

        spec.exchange()
            .expectStatus().isOk()
            .expectBody().json("{\"status\":\"ok\"}");
    }

    @Test
    void unknownPathShouldReturn404() {
        webTestClient.get()
            .uri("/api/v1/unknown")
            .exchange()
            .expectStatus().isNotFound();
    }
}
