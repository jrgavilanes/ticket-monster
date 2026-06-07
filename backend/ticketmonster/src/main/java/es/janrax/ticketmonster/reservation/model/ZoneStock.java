package es.janrax.ticketmonster.reservation.model;

import jakarta.persistence.*;

@Entity
@Table(name = "zone_stock", schema = "reservation")
public class ZoneStock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String eventId;

	@Column(nullable = false)
	private String zoneId;

	@Column(nullable = false)
	private int totalCapacity;

	@Column(nullable = false)
	private int availableCount;

	public ZoneStock() {}

	public ZoneStock(String eventId, String zoneId, int totalCapacity) {
		this.eventId = eventId;
		this.zoneId = zoneId;
		this.totalCapacity = totalCapacity;
		this.availableCount = totalCapacity;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getEventId() { return eventId; }
	public void setEventId(String eventId) { this.eventId = eventId; }
	public String getZoneId() { return zoneId; }
	public void setZoneId(String zoneId) { this.zoneId = zoneId; }
	public int getTotalCapacity() { return totalCapacity; }
	public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }
	public int getAvailableCount() { return availableCount; }
	public void setAvailableCount(int availableCount) { this.availableCount = availableCount; }

	public int getReservedCount() {
		return totalCapacity - availableCount;
	}
}
