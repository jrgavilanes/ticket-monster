package es.janrax.ticketmonster.reservation.service;

import es.janrax.ticketmonster.reservation.model.Reservation;
import es.janrax.ticketmonster.reservation.model.ReservationItem;
import es.janrax.ticketmonster.reservation.model.ZoneStock;
import es.janrax.ticketmonster.reservation.repository.ReservationRepository;
import es.janrax.ticketmonster.reservation.repository.ZoneStockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationService {

	private final ReservationRepository reservationRepository;
	private final ZoneStockRepository zoneStockRepository;
	private final DistributedLockService lockService;
	private final ReservationEventPublisher eventPublisher;

	@Value("${ticketmonster.reservation.ttl-minutes:10}")
	private int ttlMinutes;

	@Value("${ticketmonster.reservation.max-tickets-per-customer:3}")
	private int maxTicketsPerCustomer;

	public ReservationService(
			ReservationRepository reservationRepository,
			ZoneStockRepository zoneStockRepository,
			DistributedLockService lockService,
			ReservationEventPublisher eventPublisher) {
		this.reservationRepository = reservationRepository;
		this.zoneStockRepository = zoneStockRepository;
		this.lockService = lockService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public Reservation createReservation(String userId, String eventId, List<ReservationRequest> requests) {
		int totalRequested = requests.stream().mapToInt(ReservationRequest::quantity).sum();
		if (totalRequested > maxTicketsPerCustomer) {
			throw new TicketLimitExceededException(maxTicketsPerCustomer, totalRequested);
		}

		List<Reservation> existing = reservationRepository.findByUserIdAndEventIdAndStatus(
				userId, eventId, Reservation.ReservationStatus.ACTIVE);
		int existingCount = existing.stream()
				.flatMap(r -> r.getItems().stream())
				.mapToInt(ReservationItem::getQuantity)
				.sum();
		if (existingCount + totalRequested > maxTicketsPerCustomer) {
			throw new TicketLimitExceededException(maxTicketsPerCustomer, existingCount + totalRequested);
		}

		Reservation reservation = new Reservation(userId, eventId, ttlMinutes);

		for (ReservationRequest request : requests) {
			ZoneStock stock = zoneStockRepository.findByEventIdAndZoneIdForUpdate(eventId, request.zoneId())
					.orElseThrow(() -> new ZoneNotFoundException(eventId, request.zoneId()));

			if (stock.getAvailableCount() < request.quantity()) {
				throw new InsufficientStockException(eventId, request.zoneId(), stock.getAvailableCount(), request.quantity());
			}

			boolean locked = lockService.acquireLock(eventId, request.zoneId(), userId, ttlMinutes * 60);
			if (!locked) {
				throw new LockAcquisitionFailedException(eventId, request.zoneId());
			}

			stock.setAvailableCount(stock.getAvailableCount() - request.quantity());
			zoneStockRepository.save(stock);

			reservation.addItem(new ReservationItem(request.zoneId(), request.quantity()));
		}

		Reservation saved = reservationRepository.save(reservation);
		eventPublisher.publishReservationCreated(saved);
		return saved;
	}

	@Transactional(readOnly = true)
	public Reservation getReservation(String reservationId, String userId) {
		Reservation reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new ReservationNotFoundException(reservationId));
		if (!reservation.getUserId().equals(userId)) {
			throw new ReservationAccessDeniedException(reservationId);
		}
		return reservation;
	}

	@Transactional
	public void cancelReservation(String reservationId, String userId) {
		Reservation reservation = getReservation(reservationId, userId);
		if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
			throw new ReservationNotActiveException(reservationId);
		}

		for (ReservationItem item : reservation.getItems()) {
			lockService.releaseLock(reservation.getEventId(), item.getZoneId(), userId);
			zoneStockRepository.incrementStock(reservation.getEventId(), item.getZoneId(), item.getQuantity());
		}

		reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
		reservationRepository.save(reservation);
		eventPublisher.publishReservationCancelled(reservation);
	}

	@Transactional
	public void expireReservation(Reservation reservation) {
		if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) return;

		for (ReservationItem item : reservation.getItems()) {
			lockService.releaseLock(reservation.getEventId(), item.getZoneId(), reservation.getUserId());
			zoneStockRepository.incrementStock(reservation.getEventId(), item.getZoneId(), item.getQuantity());
		}

		reservation.setStatus(Reservation.ReservationStatus.EXPIRED);
		reservationRepository.save(reservation);
		eventPublisher.publishReservationExpired(reservation);
	}

	@Transactional
	public void confirmSale(String reservationId) {
		Reservation reservation = reservationRepository.findById(reservationId)
				.orElse(null);
		if (reservation == null) return;

		if (reservation.getStatus() == Reservation.ReservationStatus.EXPIRED) {
			eventPublisher.publishPaymentRefundRequired(reservation);
			return;
		}

		if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) return;

		for (ReservationItem item : reservation.getItems()) {
			lockService.releaseLock(reservation.getEventId(), item.getZoneId(), reservation.getUserId());
		}

		reservation.setStatus(Reservation.ReservationStatus.SOLD);
		reservationRepository.save(reservation);
	}

	public record ReservationRequest(String zoneId, int quantity) {}
}
