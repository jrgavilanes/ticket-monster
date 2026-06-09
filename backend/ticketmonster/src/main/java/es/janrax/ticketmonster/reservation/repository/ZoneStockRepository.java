package es.janrax.ticketmonster.reservation.repository;

import es.janrax.ticketmonster.reservation.model.ZoneStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ZoneStockRepository extends JpaRepository<ZoneStock, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT z FROM ZoneStock z WHERE z.eventId = :eventId AND z.zoneId = :zoneId")
	Optional<ZoneStock> findByEventIdAndZoneIdForUpdate(@Param("eventId") String eventId, @Param("zoneId") String zoneId);

	List<ZoneStock> findByEventId(String eventId);

	void deleteByEventId(String eventId);

	@Modifying
	@Query("UPDATE ZoneStock z SET z.availableCount = z.availableCount + :quantity WHERE z.eventId = :eventId AND z.zoneId = :zoneId")
	int incrementStock(@Param("eventId") String eventId, @Param("zoneId") String zoneId, @Param("quantity") int quantity);
}
