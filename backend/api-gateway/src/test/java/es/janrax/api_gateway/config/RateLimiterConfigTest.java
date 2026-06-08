package es.janrax.api_gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterConfigTest {

    private RateLimiterConfig config;

    @BeforeEach
    void setUp() {
        config = new RateLimiterConfig();
    }

    @Test
    void shouldResolveKeyFromXUserIdHeader() {
        var request = MockServerHttpRequest
            .get("/api/v1/queue/event-1/join")
            .header("X-User-Id", "user-123")
            .build();
        var exchange = MockServerWebExchange.from(request);

        var key = config.userKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo("user-123");
    }

    @Test
    void shouldFallbackToRemoteAddressWhenNoHeader() {
        var request = MockServerHttpRequest
            .get("/api/v1/queue/event-1/join")
            .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
            .build();
        var exchange = MockServerWebExchange.from(request);

        var key = config.userKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo("192.168.1.1");
    }

    @Test
    void shouldReturnAnonymousWhenNoHeaderAndNoRemoteAddress() {
        var request = MockServerHttpRequest
            .get("/api/v1/queue/event-1/join")
            .build();
        var exchange = MockServerWebExchange.from(request);

        var key = config.userKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo("anonymous");
    }
}
