package es.janrax.ticketmonster.reservation.repository;

import es.janrax.ticketmonster.reservation.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

	List<Reservation> findByUserIdAndEventIdAndStatus(String userId, String eventId, Reservation.ReservationStatus status);

	@Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE' AND r.expiresAt < :now")
	List<Reservation> findExpiredActiveReservations(@Param("now") LocalDateTime now);

	long countByStatus(Reservation.ReservationStatus status);
}
