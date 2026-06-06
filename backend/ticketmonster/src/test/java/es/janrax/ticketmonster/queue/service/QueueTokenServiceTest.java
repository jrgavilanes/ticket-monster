package es.janrax.ticketmonster.queue.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class QueueTokenServiceTest {

	private QueueTokenService tokenService;

	@BeforeEach
	void setUp() {
		tokenService = new QueueTokenService();
		ReflectionTestUtils.setField(tokenService, "tokenTtlMinutes", 5);
		ReflectionTestUtils.setField(tokenService, "tokenSecret", "test-secret");
	}

	@Test
	void issueAndValidateToken_shouldReturnClaims() {
		String userId = "user-123";
		String eventId = "event-456";
		String ticketId = "ticket-789";

		String token = tokenService.issueToken(userId, eventId, ticketId);
		assertThat(token).isNotEmpty();
		assertThat(token).contains(".");

		QueueTokenService.QueueTokenClaims claims = tokenService.validateToken(token);
		assertThat(claims).isNotNull();
		assertThat(claims.userId()).isEqualTo(userId);
		assertThat(claims.eventId()).isEqualTo(eventId);
		assertThat(claims.queueTicketId()).isEqualTo(ticketId);
		assertThat(claims.expiresAt()).isPositive();
	}

	@Test
	void validateToken_shouldReturnNull_whenTokenIsNull() {
		assertThat(tokenService.validateToken(null)).isNull();
	}

	@Test
	void validateToken_shouldReturnNull_whenTokenHasNoDot() {
		assertThat(tokenService.validateToken("invalid")).isNull();
	}

	@Test
	void validateToken_shouldReturnNull_whenSignatureIsInvalid() {
		String token = tokenService.issueToken("user", "event", "ticket");
		String tampered = token.substring(0, token.length() - 1) + "X";
		assertThat(tokenService.validateToken(tampered)).isNull();
	}

	@Test
	void validateToken_shouldReturnNull_whenPayloadHasWrongFormat() {
		String badToken = java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString("invalid-format".getBytes()) + ".signature";
		assertThat(tokenService.validateToken(badToken)).isNull();
	}
}
