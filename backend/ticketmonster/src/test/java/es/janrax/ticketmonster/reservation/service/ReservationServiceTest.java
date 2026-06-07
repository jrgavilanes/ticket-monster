package es.janrax.ticketmonster.reservation.service;

import es.janrax.ticketmonster.reservation.model.Reservation;
import es.janrax.ticketmonster.reservation.model.ReservationItem;
import es.janrax.ticketmonster.reservation.model.ZoneStock;
import es.janrax.ticketmonster.reservation.repository.ReservationRepository;
import es.janrax.ticketmonster.reservation.repository.ZoneStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private ZoneStockRepository zoneStockRepository;

	@Mock
	private DistributedLockService lockService;

	@Mock
	private ReservationEventPublisher eventPublisher;

	private ReservationService reservationService;

	private static final String USER_ID = "user-123";
	private static final String EVENT_ID = "event-456";
	private static final String ZONE_ID = "zone-campo";
	private static final int MAX_TICKETS = 3;

	@BeforeEach
	void setUp() {
		reservationService = new ReservationService(reservationRepository, zoneStockRepository,
				lockService, eventPublisher);
		ReflectionTestUtils.setField(reservationService, "ttlMinutes", 10);
		ReflectionTestUtils.setField(reservationService, "maxTicketsPerCustomer", MAX_TICKETS);
	}

	@Test
	void createReservation_shouldCreateSuccessfully() {
		ZoneStock stock = new ZoneStock(EVENT_ID, ZONE_ID, 50000);
		stock.setAvailableCount(49000);
		Reservation savedReservation = new Reservation(USER_ID, EVENT_ID, 10);
		savedReservation.addItem(new ReservationItem(ZONE_ID, 2));

		when(reservationRepository.findByUserIdAndEventIdAndStatus(USER_ID, EVENT_ID, Reservation.ReservationStatus.ACTIVE))
			.thenReturn(List.of());
		when(zoneStockRepository.findByEventIdAndZoneIdForUpdate(EVENT_ID, ZONE_ID))
			.thenReturn(Optional.of(stock));
		when(lockService.acquireLock(EVENT_ID, ZONE_ID, USER_ID, 600)).thenReturn(true);
		when(reservationRepository.save(any(Reservation.class))).thenReturn(savedReservation);
		doNothing().when(eventPublisher).publishReservationCreated(any(Reservation.class));

		Reservation result = reservationService.createReservation(USER_ID, EVENT_ID,
				List.of(new ReservationService.ReservationRequest(ZONE_ID, 2)));

		assertThat(result).isNotNull();
		assertThat(result.getUserId()).isEqualTo(USER_ID);
		assertThat(result.getEventId()).isEqualTo(EVENT_ID);
		assertThat(stock.getAvailableCount()).isEqualTo(48998);
		verify(zoneStockRepository).save(stock);
		verify(eventPublisher).publishReservationCreated(savedReservation);
	}

	@Test
	void createReservation_shouldThrow_whenTicketLimitExceeded() {
		assertThatThrownBy(() ->
			reservationService.createReservation(USER_ID, EVENT_ID,
					List.of(new ReservationService.ReservationRequest(ZONE_ID, 5)))
		).isInstanceOf(TicketLimitExceededException.class)
		 .hasMessageContaining("3");
	}

	@Test
	void createReservation_shouldThrow_whenExistingActiveReservationsExceedLimit() {
		Reservation existing = new Reservation(USER_ID, EVENT_ID, 10);
		existing.addItem(new ReservationItem(ZONE_ID, 2));

		when(reservationRepository.findByUserIdAndEventIdAndStatus(USER_ID, EVENT_ID,
				Reservation.ReservationStatus.ACTIVE)).thenReturn(List.of(existing));

		assertThatThrownBy(() ->
			reservationService.createReservation(USER_ID, EVENT_ID,
					List.of(new ReservationService.ReservationRequest(ZONE_ID, 2)))
		).isInstanceOf(TicketLimitExceededException.class);
	}

	@Test
	void createReservation_shouldThrow_whenZoneNotFound() {
		when(reservationRepository.findByUserIdAndEventIdAndStatus(USER_ID, EVENT_ID,
				Reservation.ReservationStatus.ACTIVE)).thenReturn(List.of());
		when(zoneStockRepository.findByEventIdAndZoneIdForUpdate(EVENT_ID, ZONE_ID))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() ->
			reservationService.createReservation(USER_ID, EVENT_ID,
					List.of(new ReservationService.ReservationRequest(ZONE_ID, 2)))
		).isInstanceOf(ZoneNotFoundException.class);
	}

	@Test
	void createReservation_shouldThrow_whenInsufficientStock() {
		ZoneStock stock = new ZoneStock(EVENT_ID, ZONE_ID, 50000);
		stock.setAvailableCount(1);

		when(reservationRepository.findByUserIdAndEventIdAndStatus(USER_ID, EVENT_ID,
				Reservation.ReservationStatus.ACTIVE)).thenReturn(List.of());
		when(zoneStockRepository.findByEventIdAndZoneIdForUpdate(EVENT_ID, ZONE_ID))
			.thenReturn(Optional.of(stock));

		assertThatThrownBy(() ->
			reservationService.createReservation(USER_ID, EVENT_ID,
					List.of(new ReservationService.ReservationRequest(ZONE_ID, 2)))
		).isInstanceOf(InsufficientStockException.class);
	}

	@Test
	void createReservation_shouldThrow_whenLockNotAcquired() {
		ZoneStock stock = new ZoneStock(EVENT_ID, ZONE_ID, 50000);

		when(reservationRepository.findByUserIdAndEventIdAndStatus(USER_ID, EVENT_ID,
				Reservation.ReservationStatus.ACTIVE)).thenReturn(List.of());
		when(zoneStockRepository.findByEventIdAndZoneIdForUpdate(EVENT_ID, ZONE_ID))
			.thenReturn(Optional.of(stock));
		when(lockService.acquireLock(EVENT_ID, ZONE_ID, USER_ID, 600)).thenReturn(false);

		assertThatThrownBy(() ->
			reservationService.createReservation(USER_ID, EVENT_ID,
					List.of(new ReservationService.ReservationRequest(ZONE_ID, 2)))
		).isInstanceOf(LockAcquisitionFailedException.class);
	}

	@Test
	void getReservation_shouldReturnReservation_whenOwnedByUser() {
		Reservation reservation = new Reservation(USER_ID, EVENT_ID, 10);
		reservation.setId("res-1");
		when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

		Reservation result = reservationService.getReservation("res-1", USER_ID);

		assertThat(result.getId()).isEqualTo("res-1");
		assertThat(result.getUserId()).isEqualTo(USER_ID);
	}

	@Test
	void getReservation_shouldThrow_whenNotFound() {
		when(reservationRepository.findById("res-unknown")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> reservationService.getReservation("res-unknown", USER_ID))
			.isInstanceOf(ReservationNotFoundException.class);
	}

	@Test
	void getReservation_shouldThrow_whenAccessDenied() {
		Reservation reservation = new Reservation("other-user", EVENT_ID, 10);
		when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

		assertThatThrownBy(() -> reservationService.getReservation("res-1", USER_ID))
			.isInstanceOf(ReservationAccessDeniedException.class);
	}

	@Test
	void cancelReservation_shouldCancelActiveReservation() {
		Reservation reservation = new Reservation(USER_ID, EVENT_ID, 10);
		reservation.setId("res-1");
		reservation.addItem(new ReservationItem(ZONE_ID, 2));

		when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

		reservationService.cancelReservation("res-1", USER_ID);

		assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CANCELLED);
		verify(lockService).releaseLock(EVENT_ID, ZONE_ID, USER_ID);
		verify(zoneStockRepository).incrementStock(EVENT_ID, ZONE_ID, 2);
		verify(reservationRepository).save(reservation);
		verify(eventPublisher).publishReservationCancelled(reservation);
	}

	@Test
	void cancelReservation_shouldThrow_whenNotActive() {
		Reservation reservation = new Reservation(USER_ID, EVENT_ID, 10);
		reservation.setStatus(Reservation.ReservationStatus.EXPIRED);

		when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

		assertThatThrownBy(() -> reservationService.cancelReservation("res-1", USER_ID))
			.isInstanceOf(ReservationNotActiveException.class);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	void expireReservation_shouldExpireActiveReservation() {
		Reservation reservation = new Reservation(USER_ID, EVENT_ID, 10);
		reservation.setId("res-1");
		reservation.addItem(new ReservationItem(ZONE_ID, 2));

		reservationService.expireReservation(reservation);

		assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.EXPIRED);
		verify(lockService).releaseLock(EVENT_ID, ZONE_ID, USER_ID);
		verify(zoneStockRepository).incrementStock(EVENT_ID, ZONE_ID, 2);
		verify(reservationRepository).save(reservation);
		verify(eventPublisher).publishReservationExpired(reservation);
	}

	@Test
	void expireReservation_shouldSkip_whenNotActive() {
		Reservation reservation = new Reservation(USER_ID, EVENT_ID, 10);
		reservation.setStatus(Reservation.ReservationStatus.SOLD);

		reservationService.expireReservation(reservation);

		verify(reservationRepository, never()).save(any());
		verify(eventPublisher, never()).publishReservationExpired(any());
	}

	@Test
	void confirmSale_shouldMarkAsSold_whenActive() {
		Reservation reservation = new Reservation(USER_ID, EVENT_ID, 10);
		reservation.setId("res-1");
		reservation.addItem(new ReservationItem(ZONE_ID, 2));

		when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

		reservationService.confirmSale("res-1");

		assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.SOLD);
		verify(lockService).releaseLock(EVENT_ID, ZONE_ID, USER_ID);
		verify(reservationRepository).save(reservation);
	}

	@Test
	void confirmSale_shouldRequestRefund_whenExpired() {
		Reservation reservation = new Reservation(USER_ID, EVENT_ID, 10);
		reservation.setStatus(Reservation.ReservationStatus.EXPIRED);

		when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

		reservationService.confirmSale("res-1");

		verify(eventPublisher).publishPaymentRefundRequired(reservation);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	void confirmSale_shouldDoNothing_whenNotFound() {
		when(reservationRepository.findById("res-unknown")).thenReturn(Optional.empty());

		reservationService.confirmSale("res-unknown");

		verifyNoInteractions(lockService, zoneStockRepository, eventPublisher);
	}
}
