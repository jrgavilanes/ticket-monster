package es.janrax.api_gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import net.minidev.json.JSONValue;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public abstract class GatewayTestBase {

    protected static final WireMockServer wireMock = new WireMockServer(
        WireMockConfiguration.options().dynamicPort()
    );

    protected static final RSAKey rsaKey;
    protected static final String ISSUER_PATH = "/auth/realms/ticket-monster";
    protected static final String JWKS_PATH = "/auth/realms/ticket-monster/protocol/openid-connect/certs";

    @LocalServerPort
    protected int port;

    protected WebTestClient webTestClient;

    static {
        wireMock.start();
        Runtime.getRuntime().addShutdownHook(new Thread(wireMock::stop));
        rsaKey = generateRsaKey();
    }

    private static RSAKey generateRsaKey() {
        try {
            var keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            var keyPair = keyGen.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setupWebTestClient() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    protected String validToken() {
        return createToken(new Date(), new Date(System.currentTimeMillis() + 3600_000));
    }

    protected String expiredToken() {
        return createToken(
            new Date(System.currentTimeMillis() - 7200_000),
            new Date(System.currentTimeMillis() - 3600_000)
        );
    }

    private String createToken(Date issueTime, Date expirationTime) {
        try {
            var signer = new RSASSASigner(rsaKey);
            var claims = new JWTClaimsSet.Builder()
                .subject("user-123")
                .issuer("http://localhost:" + wireMock.port() + ISSUER_PATH)
                .issueTime(issueTime)
                .expirationTime(expirationTime)
                .claim("realm_access", Map.of("roles", List.of("admin", "user")))
                .build();
            var signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims
            );
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void setupMockJwksEndpoint() {
        wireMock.stubFor(get(urlPathEqualTo(JWKS_PATH))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(JSONValue.toJSONString(new JWKSet(rsaKey).toJSONObject()))));
    }

    protected void setupDefaultBackendStub() {
        wireMock.stubFor(any(anyUrl())
            .atPriority(10)
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"status\":\"ok\"}")));
    }

    @DynamicPropertySource
    static void configureGatewayUri(DynamicPropertyRegistry registry) {
        registry.add("MONOLITH_URI", wireMock::baseUrl);
    }

    @DynamicPropertySource
    static void configureSecurity(DynamicPropertyRegistry registry) {
        registry.add("KEYCLOAK_ISSUER_URI",
            () -> "http://localhost:" + wireMock.port() + ISSUER_PATH);
        registry.add("KEYCLOAK_JWK_SET_URI",
            () -> "http://localhost:" + wireMock.port() + JWKS_PATH);
    }

    @TestConfiguration
    static class TestRouteConfig {
        @Bean
        RouteLocator testRoutes(RouteLocatorBuilder builder) {
            return builder.routes()
                .route("catalog-graphql", r -> r.path("/graphql").uri(wireMock.baseUrl()))
                .route("queue-service", r -> r.path("/api/v1/queue/**").uri(wireMock.baseUrl()))
                .route("reservation-service", r -> r.path("/api/v1/reservations/**").uri(wireMock.baseUrl()))
                .route("payment-service", r -> r.path("/api/v1/payments/**").uri(wireMock.baseUrl()))
                .build();
        }
    }
}
