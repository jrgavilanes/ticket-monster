package es.janrax.ticketmonster.payment.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", schema = "payment")
public class Payment {

	@Id
	private String id;

	@Column(nullable = false)
	private String reservationId;

	@Column(nullable = false)
	private String userId;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(nullable = false, unique = true)
	private String idempotencyKey;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime confirmedAt;

	public enum PaymentStatus {
		PENDING, CONFIRMED, FAILED, REFUNDED
	}

	public Payment() {
		this.id = UUID.randomUUID().toString();
		this.createdAt = LocalDateTime.now();
		this.status = PaymentStatus.PENDING;
	}

	public Payment(String reservationId, String userId, BigDecimal amount) {
		this();
		this.reservationId = reservationId;
		this.userId = userId;
		this.amount = amount;
		this.idempotencyKey = reservationId + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getReservationId() { return reservationId; }
	public void setReservationId(String reservationId) { this.reservationId = reservationId; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public BigDecimal getAmount() { return amount; }
	public void setAmount(BigDecimal amount) { this.amount = amount; }
	public PaymentStatus getStatus() { return status; }
	public void setStatus(PaymentStatus status) { this.status = status; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	public LocalDateTime getConfirmedAt() { return confirmedAt; }
	public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
