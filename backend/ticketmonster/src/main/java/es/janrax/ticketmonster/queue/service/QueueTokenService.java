package es.janrax.ticketmonster.queue.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class QueueTokenService {

	@Value("${ticketmonster.queue.token-ttl-minutes:5}")
	private int tokenTtlMinutes;

	@Value("${ticketmonster.queue.token-secret:default-queue-secret-change-me}")
	private String tokenSecret;

	public String issueToken(String userId, String eventId, String queueTicketId) {
		long expiresAt = Instant.now().plusSeconds((long) tokenTtlMinutes * 60).getEpochSecond();
		String payload = userId + ":" + eventId + ":" + queueTicketId + ":" + expiresAt;
		String signature = sign(payload);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
				+ "." + signature;
	}

	public QueueTokenClaims validateToken(String token) {
		if (token == null || !token.contains(".")) return null;

		String[] parts = token.split("\\.", 2);
		String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
		String signature = parts[1];

		if (!sign(payload).equals(signature)) return null;

		String[] claims = payload.split(":", 4);
		if (claims.length != 4) return null;

		long expiresAt = Long.parseLong(claims[3]);
		if (Instant.now().getEpochSecond() > expiresAt) return null;

		return new QueueTokenClaims(claims[0], claims[1], claims[2], expiresAt);
	}

	private String sign(String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		} catch (Exception e) {
			throw new RuntimeException("Failed to sign token", e);
		}
	}

	public record QueueTokenClaims(String userId, String eventId, String queueTicketId, long expiresAt) {}
}
