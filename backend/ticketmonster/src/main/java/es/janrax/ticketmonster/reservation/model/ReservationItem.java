package es.janrax.ticketmonster.reservation.model;

import jakarta.persistence.*;

@Entity
@Table(name = "reservation_items", schema = "reservation")
public class ReservationItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reservation_id", nullable = false)
	private Reservation reservation;

	@Column(nullable = false)
	private String zoneId;

	@Column(nullable = false)
	private int quantity;

	public ReservationItem() {}

	public ReservationItem(String zoneId, int quantity) {
		this.zoneId = zoneId;
		this.quantity = quantity;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Reservation getReservation() { return reservation; }
	public void setReservation(Reservation reservation) { this.reservation = reservation; }
	public String getZoneId() { return zoneId; }
	public void setZoneId(String zoneId) { this.zoneId = zoneId; }
	public int getQuantity() { return quantity; }
	public void setQuantity(int quantity) { this.quantity = quantity; }
}
