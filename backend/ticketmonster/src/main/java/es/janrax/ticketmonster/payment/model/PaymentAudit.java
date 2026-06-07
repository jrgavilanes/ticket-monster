package es.janrax.ticketmonster.payment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_audit", schema = "payment")
public class PaymentAudit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String paymentId;

	@Column(nullable = false)
	private String previousStatus;

	@Column(nullable = false)
	private String newStatus;

	@Column(nullable = false)
	private String actor;

	@Column(nullable = false)
	private LocalDateTime timestamp;

	public PaymentAudit() {
		this.timestamp = LocalDateTime.now();
	}

	public PaymentAudit(String paymentId, String previousStatus, String newStatus, String actor) {
		this();
		this.paymentId = paymentId;
		this.previousStatus = previousStatus;
		this.newStatus = newStatus;
		this.actor = actor;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getPaymentId() { return paymentId; }
	public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
	public String getPreviousStatus() { return previousStatus; }
	public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
	public String getNewStatus() { return newStatus; }
	public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
	public String getActor() { return actor; }
	public void setActor(String actor) { this.actor = actor; }
	public LocalDateTime getTimestamp() { return timestamp; }
	public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
