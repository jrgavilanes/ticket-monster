package es.janrax.ticketmonster.reservation.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

	@Id
	private String id;

	@Column(nullable = false)
	private String userId;

	@Column(nullable = false)
	private String eventId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReservationStatus status;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ReservationItem> items = new ArrayList<>();

	public enum ReservationStatus {
		ACTIVE, EXPIRED, CANCELLED, SOLD
	}

	public Reservation() {
		this.id = UUID.randomUUID().toString();
		this.createdAt = LocalDateTime.now();
		this.status = ReservationStatus.ACTIVE;
	}

	public Reservation(String userId, String eventId, int ttlMinutes) {
		this();
		this.userId = userId;
		this.eventId = eventId;
		this.expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes);
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public String getEventId() { return eventId; }
	public void setEventId(String eventId) { this.eventId = eventId; }
	public ReservationStatus getStatus() { return status; }
	public void setStatus(ReservationStatus status) { this.status = status; }
	public LocalDateTime getExpiresAt() { return expiresAt; }
	public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	public List<ReservationItem> getItems() { return items; }
	public void setItems(List<ReservationItem> items) { this.items = items; }

	public void addItem(ReservationItem item) {
		items.add(item);
		item.setReservation(this);
	}
}
