package es.janrax.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({GatewayTestBase.TestRouteConfig.class, CorsTest.CorsTestConfig.class})
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
})
class CorsTest extends GatewayTestBase {

    @TestConfiguration
    static class CorsTestConfig {
        @Bean
        @Primary
        CorsWebFilter corsWebFilter() {
            var config = new CorsConfiguration();
            config.addAllowedOrigin("http://localhost:3000");
            config.addAllowedMethod("*");
            config.addAllowedHeader("*");
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);

            var source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", config);
            return new CorsWebFilter(source);
        }
    }

    @Test
    void shouldIncludeCorsHeadersForAllowedOrigin() {
        webTestClient.get()
            .uri("/graphql")
            .header("Origin", "http://localhost:3000")
            .exchange()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
    }

    @Test
    void shouldRespondToPreflightRequest() {
        webTestClient.options()
            .uri("/graphql")
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "POST")
            .exchange()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
    }

    @Test
    void shouldNotIncludeCorsHeadersForDisallowedOrigin() {
        webTestClient.get()
            .uri("/graphql")
            .header("Origin", "http://evil-site.com")
            .exchange()
            .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }
}
