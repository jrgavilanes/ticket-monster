package es.janrax.ticketmonster.queue.controller;

import es.janrax.ticketmonster.queue.service.QueueService;
import es.janrax.ticketmonster.queue.service.QueueTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

	private final QueueService queueService;
	private final QueueTokenService queueTokenService;

	public QueueController(QueueService queueService, QueueTokenService queueTokenService) {
		this.queueService = queueService;
		this.queueTokenService = queueTokenService;
	}

	@PostMapping("/{eventId}/join")
	public ResponseEntity<JoinResponse> join(
			@PathVariable String eventId,
			@AuthenticationPrincipal Jwt jwt) {
		String userId = jwt.getSubject();
		QueueService.QueueTicket ticket = queueService.join(eventId, userId);
		return ResponseEntity.ok(new JoinResponse(ticket.ticketId(), ticket.position()));
	}

	@GetMapping("/{eventId}/status")
	public ResponseEntity<StatusResponse> status(
			@PathVariable String eventId,
			@AuthenticationPrincipal Jwt jwt) {
		String userId = jwt.getSubject();
		QueueService.QueueStatus status = queueService.getStatus(eventId, userId);
		return ResponseEntity.ok(new StatusResponse(status.status(), status.position()));
	}

	@GetMapping("/{eventId}/token")
	public ResponseEntity<TokenResponse> token(
			@PathVariable String eventId,
			@AuthenticationPrincipal Jwt jwt) {
		String userId = jwt.getSubject();
		if (!queueService.isTurnReady(eventId, userId)) {
			return ResponseEntity.status(403).body(new TokenResponse(null, "Turn has not arrived"));
		}
		String queueTicketId = userId;
		String token = queueTokenService.issueToken(userId, eventId, queueTicketId);
		queueService.clearTurnReady(eventId, userId);
		return ResponseEntity.ok(new TokenResponse(token, null));
	}

	public record JoinResponse(String ticketId, long position) {}
	public record StatusResponse(String status, long position) {}
	public record TokenResponse(String token, String error) {}
}
