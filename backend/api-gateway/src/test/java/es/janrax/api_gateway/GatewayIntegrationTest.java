package es.janrax.api_gateway;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayTestBase.TestRouteConfig.class)
@TestPropertySource(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
		"spring.data.redis.host=localhost",
		"spring.data.redis.port=6379"
})
class GatewayIntegrationTest extends GatewayTestBase {

	@BeforeEach
	void setUp() {
		wireMock.resetAll();
		setupMockJwksEndpoint();
		setupDefaultBackendStub();
	}

	@Test
	void adminCreatesCatalog() {
		String adminToken = validToken();

		// 1. Crear artista
		wireMock.stubFor(post(urlPathEqualTo("/graphql"))
				.withRequestBody(containing("createArtist"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"data":{"createArtist":{"id":"artist-1","name":"Foo Fighters","genre":"Rock"}}}
								""")));

		webTestClient.post().uri("/graphql")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"query\":\"mutation { createArtist(input: { name: \\\"Foo Fighters\\\", genre: \\\"Rock\\\" }) { id name } }\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.data.createArtist.id").isEqualTo("artist-1");

		// 2. Crear venue
		wireMock.stubFor(post(urlPathEqualTo("/graphql"))
				.withRequestBody(containing("createVenue"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"data":{"createVenue":{"id":"venue-1","name":"Wembley","totalCapacity":90000}}}
								""")));

		webTestClient.post().uri("/graphql")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"query\":\"mutation { createVenue(input: { name: \\\"Wembley\\\", totalCapacity: 90000 }) { id name } }\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.data.createVenue.id").isEqualTo("venue-1");

		// 3. Crear evento con zonas
		wireMock.stubFor(post(urlPathEqualTo("/graphql"))
				.withRequestBody(containing("createEvent"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"data":{"createEvent":{"id":"event-1","name":"Foo Fighters Live","status":"DRAFT","zones":[{"id":"zone-pista","name":"Pista","capacity":40000,"price":80.0},{"id":"zone-grada","name":"Grada","capacity":30000,"price":120.0}]}}}
								""")));

		webTestClient.post().uri("/graphql")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"query\":\"mutation { createEvent(input: { name: \\\"Foo Fighters Live\\\", type: CONCERT, date: \\\"2025-12-25T22:00:00\\\", venueId: \\\"venue-1\\\", zones: [{ name: \\\"Pista\\\", capacity: 40000, price: 80.0 }, { name: \\\"Grada\\\", capacity: 30000, price: 120.0 }] }) { id name status zones { id name } } }\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.data.createEvent.id").isEqualTo("event-1");

		// 4. Publicar evento
		wireMock.stubFor(post(urlPathEqualTo("/graphql"))
				.withRequestBody(containing("updateEvent"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"data":{"updateEvent":{"id":"event-1","name":"Foo Fighters Live","status":"PUBLISHED"}}}
								""")));

		webTestClient.post().uri("/graphql")
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"query\":\"mutation { updateEvent(id: \\\"event-1\\\", input: { status: PUBLISHED }) { id name status } }\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.data.updateEvent.status").isEqualTo("PUBLISHED");
	}

	@Test
	void publicUserQueriesEventsAndAvailability() {
		// 1. Query eventos (público, sin token)
		wireMock.stubFor(post(urlPathEqualTo("/graphql"))
				.withRequestBody(containing("events"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"data":{"events":{"content":[{"id":"event-1","name":"Foo Fighters Live","venue":{"name":"Wembley"},"zones":[{"id":"zone-pista","name":"Pista","capacity":40000,"price":80.0}]}],"totalElements":1}}}
								""")));

		webTestClient.post().uri("/graphql")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"query\":\"{ events(page:0, size:10) { content { id name venue { name } zones { id name capacity price } } } }\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.data.events.totalElements").isEqualTo(1)
				.jsonPath("$.data.events.content[0].name").isEqualTo("Foo Fighters Live");

		// 2. Query disponibilidad (público, sin token)
		wireMock.stubFor(post(urlPathEqualTo("/graphql"))
				.withRequestBody(containing("availability"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"data":{"availability":[{"zoneName":"Pista","totalCapacity":40000,"reservedCount":0,"availableCount":40000},{"zoneName":"Grada","totalCapacity":30000,"reservedCount":0,"availableCount":30000}]}}
								""")));

		webTestClient.post().uri("/graphql")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"query\":\"{ availability(eventId: \\\"event-1\\\") { zoneName totalCapacity reservedCount availableCount } }\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.data.availability[0].zoneName").isEqualTo("Pista")
				.jsonPath("$.data.availability[0].availableCount").isEqualTo(40000);
	}

	@Test
	void userJoinsQueueCreatesReservationAndPays() {
		String userToken = validToken();

		// 1. Join queue
		wireMock.stubFor(post(urlPathEqualTo("/api/v1/queue/event-1/join"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"ticketId":"ticket-1","position":1}
								""")));

		webTestClient.post().uri("/api/v1/queue/event-1/join")
				.header("Authorization", "Bearer " + userToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.ticketId").isEqualTo("ticket-1")
				.jsonPath("$.position").isEqualTo(1);

		// 2. Queue status
		wireMock.stubFor(get(urlPathEqualTo("/api/v1/queue/event-1/status"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"status":"TURN_READY","position":0}
								""")));

		webTestClient.get().uri("/api/v1/queue/event-1/status")
				.header("Authorization", "Bearer " + userToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("TURN_READY");

		// 3. Queue token
		wireMock.stubFor(get(urlPathEqualTo("/api/v1/queue/event-1/token"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"token":"access-token-123","error":null}
								""")));

		webTestClient.get().uri("/api/v1/queue/event-1/token")
				.header("Authorization", "Bearer " + userToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.token").isEqualTo("access-token-123");

		// 4. Crear reserva
		wireMock.stubFor(post(urlPathEqualTo("/api/v1/reservations"))
				.willReturn(aResponse()
						.withStatus(201)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"id":"reservation-1","eventId":"event-1","status":"ACTIVE","items":[{"zoneId":"zone-pista","quantity":2}],"expiresAt":"2025-12-25T22:10:00","createdAt":"2025-12-25T22:00:00"}
								""")));

		webTestClient.post().uri("/api/v1/reservations")
				.header("Authorization", "Bearer " + userToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"eventId\":\"event-1\",\"items\":[{\"zoneId\":\"zone-pista\",\"quantity\":2}]}")
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.id").isEqualTo("reservation-1")
				.jsonPath("$.status").isEqualTo("ACTIVE");

		// 5. Iniciar pago
		wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
				.willReturn(aResponse()
						.withStatus(201)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"id":"payment-1","reservationId":"reservation-1","amount":160.0,"status":"PENDING","createdAt":"2025-12-25T22:00:05"}
								""")));

		webTestClient.post().uri("/api/v1/payments")
				.header("Authorization", "Bearer " + userToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"reservationId\":\"reservation-1\",\"amount\":160.0}")
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.id").isEqualTo("payment-1")
				.jsonPath("$.status").isEqualTo("PENDING");

		// 6. Confirmar pago
		wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments/payment-1/confirm"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"id":"payment-1","status":"CONFIRMED","confirmedAt":"2025-12-25T22:00:06","reservationId":"reservation-1","amount":"160.00"}
								""")));

		webTestClient.post().uri("/api/v1/payments/payment-1/confirm")
				.header("Authorization", "Bearer " + userToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"idempotencyKey\":\"idem-1\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.id").isEqualTo("payment-1")
				.jsonPath("$.status").isEqualTo("CONFIRMED");
	}

	@Test
	void authIsEnforcedCorrectly() {
		wireMock.stubFor(post(urlPathEqualTo("/graphql"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"data\":{\"events\":{\"content\":[]}}}")));

		// GraphQL público sin token → 200 OK
		webTestClient.post().uri("/graphql")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"query\":\"{ events { content { id } } }\"}")
				.exchange()
				.expectStatus().isOk();

		// Protected endpoint sin token → 401
		webTestClient.get().uri("/api/v1/queue/event-1/join")
				.exchange()
				.expectStatus().isUnauthorized();

		webTestClient.get().uri("/api/v1/reservations/res-1")
				.exchange()
				.expectStatus().isUnauthorized();

		webTestClient.get().uri("/api/v1/payments/pay-1")
				.exchange()
				.expectStatus().isUnauthorized();

		// Protected endpoint con token válido → 200 OK
		webTestClient.get().uri("/api/v1/queue/event-1/join")
				.header("Authorization", "Bearer " + validToken())
				.exchange()
				.expectStatus().isOk();

		// Protected endpoint con token expirado → 401
		webTestClient.get().uri("/api/v1/queue/event-1/join")
				.header("Authorization", "Bearer " + expiredToken())
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void healthEndpointIsPublic() {
		wireMock.stubFor(get(urlPathEqualTo("/actuator/health"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"status\":\"UP\"}")));

		webTestClient.get().uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void mutationWithoutTokenReturnsError() {
		wireMock.stubFor(post(urlPathEqualTo("/graphql"))
				.withRequestBody(containing("deleteEvent"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""
								{"errors":[{"message":"Unauthorized","locations":[{"line":1,"column":12}],"path":["deleteEvent"],"extensions":{"classification":"UNAUTHORIZED"}}],"data":null}
								""")));

		webTestClient.post().uri("/graphql")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"query\":\"mutation { deleteEvent(id: \\\"event-1\\\") }\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.errors[0].message").isEqualTo("Unauthorized");
	}
}
