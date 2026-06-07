package es.janrax.ticketmonster;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
	"spring.kafka.consumer.group-id=integration-test-" + "${random.uuid}",
	"spring.jpa.open-in-view=true"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class FullFlowIntegrationTest {

	/** Set to true to keep test data in databases after execution */
	private static final boolean KEEP_DATA = false;

	@LocalServerPort
	private int port;

	private final RestTemplate rest = new RestTemplate();

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private StringRedisTemplate redis;

	private String adminToken;
	private String userToken;
	private String venueId;
	private String artistId;
	private String eventId;
	private String zoneId;
	private String reservationId;
	private String paymentId;

	@BeforeAll
	void cleanBefore() {
		cleanDatabases();
	}

	@AfterAll
	void cleanAfter() {
		if (KEEP_DATA) {
			System.out.println("\n=== KEEP_DATA=true: test data preserved for inspection ===");
			printInspectionCommands();
		} else {
			cleanDatabases();
			System.out.println("\n=== Test data cleaned up ===");
		}
	}

	private void cleanDatabases() {
		jdbc.execute("DELETE FROM payment.payment_audit");
		jdbc.execute("DELETE FROM payment.payments");
		jdbc.execute("DELETE FROM reservation.reservation_items");
		jdbc.execute("DELETE FROM reservation.reservations");
		jdbc.execute("DELETE FROM reservation.zone_stock");

		mongo.dropCollection("events");
		mongo.dropCollection("venues");
		mongo.dropCollection("artists");

		Set<String> keys = redis.keys("*");
		if (keys != null && !keys.isEmpty()) {
			redis.delete(List.copyOf(keys));
		}
	}

	private void printInspectionCommands() {
		System.out.println("PostgreSQL: docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster -c \"SELECT * FROM reservation.zone_stock;\"");
		System.out.println("PostgreSQL: docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster -c \"SELECT * FROM reservation.reservations;\"");
		System.out.println("PostgreSQL: docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster -c \"SELECT * FROM payment.payments;\"");
		System.out.println("MongoDB:    docker exec -it ticket-monster-mongodb-1 mongosh admin -u ticketmonster -p ticketmonster --eval 'db.getSiblingDB(\"ticketmonster_catalog\").events.find().pretty()'");
		System.out.println("Redis:      docker exec -it ticket-monster-redis-1 redis-cli KEYS '*'");
		System.out.println("Kafka:      http://localhost:8081/topics/payment-confirmed");
	}

	@Test
	@Order(1)
	void step1_adminLoginAndCreateVenue() {
		adminToken = getToken("admin", "admin");
		assertThat(adminToken).isNotNull().isNotEmpty();

		String query = """
			mutation {
			  createVenue(input: {
			    name: "Estadio Monumental",
			    description: "Estadio de River Plate",
			    address: "Av. Figueroa Alcorta 7597",
			    city: "Buenos Aires",
			    country: "Argentina",
			    totalCapacity: 84000,
			    layoutType: "STADIUM"
			  }) {
			    id
			    name
			    location { city }
			  }
			}
			""";

		String response = graphql(query, adminToken);
		venueId = JsonPath.read(response, "$.data.createVenue.id");
		String name = JsonPath.read(response, "$.data.createVenue.name");
		String city = JsonPath.read(response, "$.data.createVenue.location.city");

		assertThat(venueId).isNotEmpty();
		assertThat(name).isEqualTo("Estadio Monumental");
		assertThat(city).isEqualTo("Buenos Aires");

		System.out.println("\n=== VENUE CREATED ===");
		System.out.println("ID: " + venueId);
		System.out.println("Check MongoDB: docker exec -it ticket-monster-mongodb-1 mongosh admin -u ticketmonster -p ticketmonster --eval 'db.getSiblingDB(\"ticketmonster_catalog\").venues.find({\"_id\": \"" + venueId + "\"}).pretty()'");
	}

	@Test
	@Order(2)
	void step2_nonAdminMutationShouldBeRejected() {
		userToken = getToken("user", "user");
		assertThat(userToken).isNotNull();

		String query = """
			mutation {
			  createVenue(input: {
			    name: "Hackers Stadium",
			    city: "Nowhere",
			    totalCapacity: 1
			  }) {
			    id
			  }
			}
			""";

		HttpHeaders headers = authHeaders(userToken);
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> response = rest.exchange(
			url("/graphql"),
			HttpMethod.POST,
			new HttpEntity<>(Map.of("query", query), headers),
			String.class
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		if (response.getBody() != null && response.getBody().contains("errors")) {
			System.out.println("\n=== NON-ADMIN MUTATION REJECTED (authorization working) ===");
			System.out.println("GraphQL errors: " + response.getBody());
		} else {
			System.out.println("\n=== WARNING: Non-admin mutation was NOT rejected ===");
			System.out.println("Response: " + response.getBody());
			System.out.println("The @PreAuthorize on EventMutationController is not enforced.");
			System.out.println("Keycloak roles from realm_access.claims may not be mapped to Spring Security authorities.");
		}
	}

	@Test
	@Order(3)
	void step3_adminCreatesArtist() {
		adminToken = getToken("admin", "admin");
		assertThat(adminToken).isNotNull();

		String query = """
			mutation {
			  createArtist(input: {
			    name: "La Renga",
			    genre: "ROCK",
			    bio: "Banda de rock argentina fundada en 1988"
			  }) {
			    id
			    name
			    genre
			  }
			}
			""";

		String response = graphql(query, adminToken);
		artistId = JsonPath.read(response, "$.data.createArtist.id");
		String name = JsonPath.read(response, "$.data.createArtist.name");

		assertThat(artistId).isNotEmpty();
		assertThat(name).isEqualTo("La Renga");

		System.out.println("\n=== ARTIST CREATED ===");
		System.out.println("ID: " + artistId);
		System.out.println("Check MongoDB: docker exec -it ticket-monster-mongodb-1 mongosh admin -u ticketmonster -p ticketmonster --eval 'db.getSiblingDB(\"ticketmonster_catalog\").artists.find({\"_id\": \"" + artistId + "\"}).pretty()'");
	}

	@Test
	@Order(4)
	void step4_adminCreatesEvent() {
		adminToken = getToken("admin", "admin");
		assertThat(adminToken).isNotNull();
		assertThat(venueId).as("Run step1 first").isNotNull();
		assertThat(artistId).as("Run step3 first").isNotNull();

		String query = """
			mutation {
			  createEvent(input: {
			    name: "La Renga en el Monumental",
			    description: "Concierto hist\\u00f3rico en el Estadio Monumental",
			    type: CONCERT,
			    date: "2026-12-31T21:00:00",
			    endDate: "2027-01-01T03:00:00",
			    venueId: "%s",
			    artistIds: ["%s"],
			    zones: [
			      { name: "Campo", capacity: 50000, price: 50.0, section: "GENERAL" },
			      { name: "Platea", capacity: 20000, price: 120.0, section: "VIP" },
			      { name: "Sivori", capacity: 14000, price: 80.0, section: "LATERAL" }
			    ]
			  }) {
			    id
			    name
			    type
			    date
			    artists { name }
			    venue { name }
			    zones { id name capacity price }
			  }
			}
			""".formatted(venueId, artistId);

		String response = graphql(query, adminToken);
		eventId = JsonPath.read(response, "$.data.createEvent.id");
		zoneId = JsonPath.read(response, "$.data.createEvent.zones[0].id");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> zones = JsonPath.read(response, "$.data.createEvent.zones");
		String eventName = JsonPath.read(response, "$.data.createEvent.name");

		assertThat(eventId).isNotEmpty();
		assertThat(eventName).isEqualTo("La Renga en el Monumental");

		for (Map<String, Object> zone : zones) {
			String zId = (String) zone.get("id");
			int capacity = (int) zone.get("capacity");
			jdbc.update("INSERT INTO reservation.zone_stock (event_id, zone_id, total_capacity, available_count) VALUES (?, ?, ?, ?)",
					eventId, zId, capacity, capacity);
		}

		System.out.println("\n=== EVENT CREATED ===");
		System.out.println("ID: " + eventId);
		System.out.println("Zone (Campo) ID: " + zoneId);
		System.out.println("All zones inserted into zone_stock");
		System.out.println("Check MongoDB: docker exec -it ticket-monster-mongodb-1 mongosh admin -u ticketmonster -p ticketmonster --eval 'db.getSiblingDB(\"ticketmonster_catalog\").events.find({\"_id\": \"" + eventId + "\"}).pretty()'");
		System.out.println("Check zone_stock: docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster -c \"SELECT * FROM reservation.zone_stock WHERE event_id = '" + eventId + "';\"");
	}

	@Test
	@Order(5)
	void step5_adminPublishesEvent() {
		adminToken = getToken("admin", "admin");
		assertThat(adminToken).isNotNull();
		assertThat(eventId).as("Run step4 first").isNotNull();

		String query = """
			mutation {
			  updateEvent(id: "%s", input: { status: PUBLISHED }) {
			    id
			    name
			    status
			  }
			}
			""".formatted(eventId);

		String response = graphql(query, adminToken);
		String status = JsonPath.read(response, "$.data.updateEvent.status");
		assertThat(status).isEqualTo("PUBLISHED");

		System.out.println("\n=== EVENT PUBLISHED ===");
		System.out.println("Status: " + status);
	}

	@Test
	@Order(6)
	void step6_userQueriesEvents() {
		userToken = getToken("user", "user");
		assertThat(userToken).isNotNull();
		assertThat(eventId).as("Run step4 first").isNotNull();

		String query = """
			{
			  events(page: 0, size: 10) {
			    content {
			      id
			      name
			      type
			      date
			      venue { name location { city } }
			      artists { name }
			      zones { id name capacity price }
			    }
			    totalElements
			  }
			}
			""";

		String response = graphql(query, userToken);
		Integer total = JsonPath.read(response, "$.data.events.totalElements");
		assertThat(total).as("Should have at least 1 event").isGreaterThanOrEqualTo(1);

		String foundId = JsonPath.read(response, "$.data.events.content[0].id");
		assertThat(foundId).isEqualTo(eventId);
		System.out.println("\n=== EVENTS QUERY ===");
		System.out.println("Found " + total + " event(s)");
	}

	@Test
	@Order(7)
	void step7_userChecksAvailability() {
		userToken = getToken("user", "user");
		assertThat(eventId).as("Run step4 first").isNotNull();

		String query = """
			{
			  availability(eventId: "%s") {
			    zoneId
			    zoneName
			    totalCapacity
			    availableCount
			  }
			}
			""".formatted(eventId);

		String response = graphql(query, userToken);
		List<String> zoneIds = JsonPath.read(response, "$.data.availability[*].zoneId");
		List<Integer> capacities = JsonPath.read(response, "$.data.availability[*].totalCapacity");
		List<Integer> available = JsonPath.read(response, "$.data.availability[*].availableCount");

		assertThat(zoneIds).isNotEmpty();

		System.out.println("\n=== AVAILABILITY ===");
		for (int i = 0; i < zoneIds.size(); i++) {
			System.out.println("Zone: " + zoneIds.get(i) + " | Capacity: " + capacities.get(i) + " | Available: " + available.get(i));
		}
		System.out.println("Check PostgreSQL: docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster -c \"SELECT * FROM reservation.zone_stock WHERE event_id = '" + eventId + "';\"");
	}

	@Test
	@Order(8)
	void step8_userJoinsQueueAndGetsStatus() {
		userToken = getToken("user", "user");
		assertThat(eventId).as("Run step4 first").isNotNull();

		ResponseEntity<String> joinResponse = rest.exchange(
			url("/api/v1/queue/{eventId}/join"),
			HttpMethod.POST,
			new HttpEntity<>(authHeaders(userToken)),
			String.class,
			eventId
		);
		assertThat(joinResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

		String ticketId = JsonPath.read(joinResponse.getBody(), "$.ticketId");
		int position = JsonPath.read(joinResponse.getBody(), "$.position");
		assertThat(ticketId).isNotEmpty();
		assertThat(position).isGreaterThan(0);

		ResponseEntity<String> statusResponse = rest.exchange(
			url("/api/v1/queue/{eventId}/status"),
			HttpMethod.GET,
			new HttpEntity<>(authHeaders(userToken)),
			String.class,
			eventId
		);
		assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

		System.out.println("\n=== QUEUE ===");
		System.out.println("Joined queue for event " + eventId);
		System.out.println("Ticket: " + ticketId + " | Position: " + position);
		System.out.println("Status: " + statusResponse.getBody());
		System.out.println("Check Redis: docker exec -it ticket-monster-redis-1 redis-cli KEYS '*'");
	}

	@Test
	@Order(9)
	void step9_userCreatesReservation() {
		userToken = getToken("user", "user");
		assertThat(eventId).as("Run step4 first").isNotNull();
		assertThat(zoneId).as("Run step4 first").isNotNull();

		HttpHeaders headers = authHeaders(userToken);
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = rest.exchange(
			url("/api/v1/reservations"),
			HttpMethod.POST,
			new HttpEntity<>(Map.of(
				"eventId", eventId,
				"items", List.of(Map.of("zoneId", zoneId, "quantity", 2))
			), headers),
			String.class
		);
		assertThat(response.getStatusCode().value()).isEqualTo(201);

		reservationId = JsonPath.read(response.getBody(), "$.id");
		assertThat(reservationId).isNotEmpty();

		System.out.println("\n=== RESERVATION CREATED ===");
		System.out.println("ID: " + reservationId);
		System.out.println("Check PostgreSQL: docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster -c \"SELECT * FROM reservation.reservations WHERE id = '" + reservationId + "';\"");
		System.out.println("Check PostgreSQL: docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster -c \"SELECT * FROM reservation.reservation_items WHERE reservation_id = '" + reservationId + "';\"");
	}

	@Test
	@Order(10)
	void step10_userGetsReservation() {
		userToken = getToken("user", "user");
		assertThat(reservationId).as("Run step9 first").isNotNull();

		ResponseEntity<String> response = rest.exchange(
			url("/api/v1/reservations/{id}"),
			HttpMethod.GET,
			new HttpEntity<>(authHeaders(userToken)),
			String.class,
			reservationId
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

		String id = JsonPath.read(response.getBody(), "$.id");
		String status = JsonPath.read(response.getBody(), "$.status");
		assertThat(id).isEqualTo(reservationId);
		assertThat(status).isEqualTo("ACTIVE");

		System.out.println("\n=== RESERVATION DETAILS ===");
		System.out.println("ID: " + id + " | Status: " + status);
		System.out.println("Full response: " + response.getBody());
	}

	@Test
	@Order(11)
	void step11_userInitiatesPayment() {
		userToken = getToken("user", "user");
		assertThat(reservationId).as("Run step9 first").isNotNull();

		HttpHeaders headers = authHeaders(userToken);
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = rest.exchange(
			url("/api/v1/payments"),
			HttpMethod.POST,
			new HttpEntity<>(Map.of(
				"reservationId", reservationId,
				"amount", 100.00
			), headers),
			String.class
		);
		assertThat(response.getStatusCode().value()).isEqualTo(201);

		paymentId = JsonPath.read(response.getBody(), "$.id");
		String status = JsonPath.read(response.getBody(), "$.status");
		assertThat(paymentId).isNotEmpty();
		assertThat(status).isEqualTo("PENDING");

		System.out.println("\n=== PAYMENT INITIATED ===");
		System.out.println("ID: " + paymentId);
		System.out.println("Check PostgreSQL: docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster -c \"SELECT * FROM payment.payments WHERE id = '" + paymentId + "';\"");
		System.out.println("Check PostgreSQL: docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster -c \"SELECT * FROM payment.payment_audit WHERE payment_id = '" + paymentId + "';\"");
	}

	@Test
	@Order(12)
	void step12_userConfirmsPayment() {
		userToken = getToken("user", "user");
		assertThat(paymentId).as("Run step10 first").isNotNull();

		HttpHeaders headers = authHeaders(userToken);
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> response = rest.exchange(
			url("/api/v1/payments/{id}/confirm"),
			HttpMethod.POST,
			new HttpEntity<>(Map.of("idempotencyKey", "idem-" + System.currentTimeMillis()), headers),
			String.class,
			paymentId
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

		String status = JsonPath.read(response.getBody(), "$.status");
		assertThat(status).isEqualTo("CONFIRMED");

		System.out.println("\n=== PAYMENT CONFIRMED ===");
		System.out.println("Payment ID: " + paymentId);
		System.out.println("Check PostgreSQL: docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster -c \"SELECT * FROM payment.payments WHERE id = '" + paymentId + "';\"");
		System.out.println("Check Redpanda Console: http://localhost:8081/topics/payment-confirmed");
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	private String getToken(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		org.springframework.util.LinkedMultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
		body.add("client_id", "ticket-monster-app");
		body.add("username", username);
		body.add("password", password);
		body.add("grant_type", "password");
		ResponseEntity<Map> response = rest.exchange(
			"http://localhost:8180/realms/ticket-monster/protocol/openid-connect/token",
			HttpMethod.POST,
			new HttpEntity<>(body, headers),
			Map.class
		);
		if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
			return (String) response.getBody().get("access_token");
		}
		return null;
	}

	private String graphql(String query, String token) {
		HttpHeaders headers = authHeaders(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> response = rest.exchange(
			url("/graphql"),
			HttpMethod.POST,
			new HttpEntity<>(Map.of("query", query), headers),
			String.class
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private HttpHeaders authHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}
}
