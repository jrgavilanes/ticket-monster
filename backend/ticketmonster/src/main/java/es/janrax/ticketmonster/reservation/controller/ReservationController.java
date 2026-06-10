package es.janrax.ticketmonster.reservation.controller;

import es.janrax.ticketmonster.reservation.model.Reservation;
import es.janrax.ticketmonster.reservation.service.ReservationService;
import es.janrax.ticketmonster.reservation.service.TicketLimitExceededException;
import es.janrax.ticketmonster.reservation.service.InsufficientStockException;
import es.janrax.ticketmonster.reservation.service.LockAcquisitionFailedException;
import es.janrax.ticketmonster.reservation.service.ReservationNotFoundException;
import es.janrax.ticketmonster.reservation.service.ReservationAccessDeniedException;
import es.janrax.ticketmonster.reservation.service.ReservationNotActiveException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping
	public ResponseEntity<?> createReservation(
			@RequestBody CreateReservationRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		String userId = jwt.getSubject();
		try {
			List<ReservationService.ReservationRequest> items = request.items().stream()
					.map(i -> new ReservationService.ReservationRequest(i.zoneId(), i.quantity()))
					.toList();
			Reservation reservation = reservationService.createReservation(userId, request.eventId(), items);
			return ResponseEntity.status(201).body(toResponse(reservation));
		} catch (TicketLimitExceededException e) {
			return ResponseEntity.status(422).body(Map.of("error", e.getMessage()));
		} catch (InsufficientStockException e) {
			return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
		} catch (LockAcquisitionFailedException e) {
			return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
		}
	}

	@GetMapping("/{id}")
	@Transactional(readOnly = true)
	public ResponseEntity<?> getReservation(
			@PathVariable String id,
			@AuthenticationPrincipal Jwt jwt) {
		String userId = jwt.getSubject();
		try {
			Reservation reservation = reservationService.getReservation(id, userId);
			return ResponseEntity.ok(toResponse(reservation));
		} catch (ReservationNotFoundException e) {
			return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
		} catch (ReservationAccessDeniedException e) {
			return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> cancelReservation(
			@PathVariable String id,
			@AuthenticationPrincipal Jwt jwt) {
		String userId = jwt.getSubject();
		try {
			reservationService.cancelReservation(id, userId);
			return ResponseEntity.ok(Map.of("status", "CANCELLED"));
		} catch (ReservationNotFoundException e) {
			return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
		} catch (ReservationAccessDeniedException e) {
			return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
		} catch (ReservationNotActiveException e) {
			return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
		}
	}

	private Map<String, Object> toResponse(Reservation reservation) {
		return Map.of(
				"id", reservation.getId(),
				"userId", reservation.getUserId(),
				"eventId", reservation.getEventId(),
				"status", reservation.getStatus().name(),
				"expiresAt", reservation.getExpiresAt().toString(),
				"createdAt", reservation.getCreatedAt().toString(),
				"items", reservation.getItems().stream()
						.map(i -> Map.of("zoneId", i.getZoneId(), "quantity", i.getQuantity()))
						.toList()
		);
	}

	public record CreateReservationRequest(
			String eventId,
			List<ReservationItemRequest> items
	) {}

	public record ReservationItemRequest(String zoneId, int quantity) {}
}
