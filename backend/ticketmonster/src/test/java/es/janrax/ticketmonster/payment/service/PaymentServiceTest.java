package es.janrax.ticketmonster.payment.service;

import es.janrax.ticketmonster.payment.model.Payment;
import es.janrax.ticketmonster.payment.model.PaymentAudit;
import es.janrax.ticketmonster.payment.repository.PaymentAuditRepository;
import es.janrax.ticketmonster.payment.repository.PaymentRepository;
import es.janrax.ticketmonster.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentAuditRepository auditRepository;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	@Mock
	private ReservationService reservationService;

	@InjectMocks
	private PaymentService paymentService;

	@Captor
	private ArgumentCaptor<Payment> paymentCaptor;

	@Captor
	private ArgumentCaptor<PaymentAudit> auditCaptor;

	private static final String USER_ID = "user-123";
	private static final String RESERVATION_ID = "res-456";
	private static final BigDecimal AMOUNT = new BigDecimal("150.00");

	@Test
	void initiatePayment_shouldCreateNewPayment() {
		when(paymentRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());
		when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(auditRepository.save(any(PaymentAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Payment payment = paymentService.initiatePayment(USER_ID, RESERVATION_ID, AMOUNT);

		assertThat(payment.getUserId()).isEqualTo(USER_ID);
		assertThat(payment.getReservationId()).isEqualTo(RESERVATION_ID);
		assertThat(payment.getAmount()).isEqualByComparingTo(AMOUNT);
		assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);

		verify(paymentRepository).save(paymentCaptor.capture());
		verify(auditRepository).save(auditCaptor.capture());

		assertThat(auditCaptor.getValue().getPaymentId()).isEqualTo(paymentCaptor.getValue().getId());
		assertThat(auditCaptor.getValue().getPreviousStatus()).isEqualTo("NONE");
		assertThat(auditCaptor.getValue().getNewStatus()).isEqualTo("PENDING");
	}

	@Test
	void initiatePayment_shouldReturnExistingPayment_whenDuplicateReservation() {
		Payment existing = new Payment(RESERVATION_ID, USER_ID, AMOUNT);
		when(paymentRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(existing));

		Payment result = paymentService.initiatePayment(USER_ID, RESERVATION_ID, AMOUNT);

		assertThat(result).isSameAs(existing);
		verify(paymentRepository, never()).save(any());
	}

	@Test
	void getPayment_shouldReturnPayment_whenOwnedByUser() {
		Payment payment = new Payment(RESERVATION_ID, USER_ID, AMOUNT);
		payment.setId("pay-1");
		when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));

		Payment result = paymentService.getPayment("pay-1", USER_ID);

		assertThat(result.getId()).isEqualTo("pay-1");
		assertThat(result.getUserId()).isEqualTo(USER_ID);
	}

	@Test
	void getPayment_shouldThrow_whenNotFound() {
		when(paymentRepository.findById("pay-unknown")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> paymentService.getPayment("pay-unknown", USER_ID))
			.isInstanceOf(PaymentNotFoundException.class);
	}

	@Test
	void getPayment_shouldThrow_whenAccessDenied() {
		Payment payment = new Payment(RESERVATION_ID, "other-user", AMOUNT);
		when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentService.getPayment("pay-1", USER_ID))
			.isInstanceOf(PaymentAccessDeniedException.class);
	}

	@Test
	void confirmPayment_shouldConfirmAndPublishEvent() {
		Payment payment = new Payment(RESERVATION_ID, USER_ID, AMOUNT);
		payment.setId("pay-1");
		String idempotencyKey = "key-123";

		when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
		when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));
		when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(auditRepository.save(any(PaymentAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

		Payment result = paymentService.confirmPayment("pay-1", idempotencyKey);

		assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.CONFIRMED);
		assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);
		assertThat(result.getConfirmedAt()).isNotNull();

		verify(auditRepository).save(auditCaptor.capture());
		assertThat(auditCaptor.getValue().getPreviousStatus()).isEqualTo("PENDING");
		assertThat(auditCaptor.getValue().getNewStatus()).isEqualTo("CONFIRMED");

		verify(kafkaTemplate).send(eq("payment-confirmed"), eq("pay-1"), any(Map.class));
		verify(reservationService).confirmSale(RESERVATION_ID);
	}

	@Test
	void confirmPayment_shouldReturnExisting_whenIdempotent() {
		Payment confirmed = new Payment(RESERVATION_ID, USER_ID, AMOUNT);
		confirmed.setStatus(Payment.PaymentStatus.CONFIRMED);

		when(paymentRepository.findByIdempotencyKey("existing-key")).thenReturn(Optional.of(confirmed));

		Payment result = paymentService.confirmPayment("pay-1", "existing-key");

		assertThat(result).isSameAs(confirmed);
		verify(paymentRepository, never()).save(any());
	}

	@Test
	void confirmPayment_shouldThrow_whenPaymentNotFound() {
		when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
		when(paymentRepository.findById("pay-unknown")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> paymentService.confirmPayment("pay-unknown", "key-1"))
			.isInstanceOf(PaymentNotFoundException.class);
	}

	@Test
	void confirmPayment_shouldReturnPayment_whenAlreadyConfirmed() {
		Payment payment = new Payment(RESERVATION_ID, USER_ID, AMOUNT);
		payment.setStatus(Payment.PaymentStatus.CONFIRMED);

		when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
		when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(payment));

		Payment result = paymentService.confirmPayment("pay-1", "key-1");

		assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.CONFIRMED);
		verify(paymentRepository, never()).save(any());
	}
}
