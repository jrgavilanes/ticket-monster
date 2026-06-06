package es.janrax.ticketmonster.queue.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ListOperations<String, String> listOps;

	@Mock
	private SetOperations<String, String> setOps;

	@Mock
	private HashOperations<String, Object, Object> hashOps;

	private QueueService queueService;

	private static final String EVENT_ID = "event-123";
	private static final String USER_ID = "user-456";
	private static final String QUEUE_KEY = "queue:event-123";
	private static final String MEMBERS_KEY = "queue:members:event-123";

	@BeforeEach
	void setUp() {
		lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
		lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
		lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
		queueService = new QueueService(redisTemplate);
	}

	@Test
	void join_shouldCreateNewTicket_whenUserNotInQueue() {
		when(setOps.isMember(MEMBERS_KEY, USER_ID)).thenReturn(false);
		when(listOps.leftPush(eq(QUEUE_KEY), eq(USER_ID))).thenReturn(1L);
		when(setOps.add(eq(MEMBERS_KEY), eq(USER_ID))).thenReturn(1L);
		doNothing().when(hashOps).put(anyString(), anyString(), anyString());
		when(listOps.size(QUEUE_KEY)).thenReturn(1L);

		QueueService.QueueTicket ticket = queueService.join(EVENT_ID, USER_ID);

		assertThat(ticket.ticketId()).isNotEmpty();
		assertThat(ticket.position()).isEqualTo(1);
		verify(listOps).leftPush(QUEUE_KEY, USER_ID);
	}

	@Test
	void join_shouldReturnExistingTicket_whenUserAlreadyInQueue() {
		String ticketId = "existing-ticket";
		when(setOps.isMember(MEMBERS_KEY, USER_ID)).thenReturn(true);
		when(listOps.range(QUEUE_KEY, 0, -1)).thenReturn(List.of(USER_ID));
		when(hashOps.get(MEMBERS_KEY + ":tickets", USER_ID)).thenReturn(ticketId);

		QueueService.QueueTicket ticket = queueService.join(EVENT_ID, USER_ID);

		assertThat(ticket.ticketId()).isEqualTo(ticketId);
		assertThat(ticket.position()).isEqualTo(1);
		verify(listOps, never()).leftPush(anyString(), anyString());
	}

	@Test
	void getStatus_shouldReturnTurnReady_whenTurnIsReady() {
		String turnKey = "queue:turn:event-123";
		when(hashOps.hasKey(turnKey, USER_ID)).thenReturn(true);

		QueueService.QueueStatus status = queueService.getStatus(EVENT_ID, USER_ID);

		assertThat(status.status()).isEqualTo("TURN_READY");
		assertThat(status.position()).isZero();
	}

	@Test
	void getStatus_shouldReturnWaiting_whenUserInQueue() {
		String turnKey = "queue:turn:event-123";
		when(hashOps.hasKey(turnKey, USER_ID)).thenReturn(false);
		when(listOps.range(QUEUE_KEY, 0, -1)).thenReturn(List.of("user1", USER_ID, "user3"));

		QueueService.QueueStatus status = queueService.getStatus(EVENT_ID, USER_ID);

		assertThat(status.status()).isEqualTo("WAITING");
		assertThat(status.position()).isEqualTo(2);
	}

	@Test
	void getStatus_shouldReturnNotInQueue_whenUserNotFound() {
		when(hashOps.hasKey("queue:turn:event-123", USER_ID)).thenReturn(false);
		when(listOps.range(QUEUE_KEY, 0, -1)).thenReturn(List.of("user1", "user2"));

		QueueService.QueueStatus status = queueService.getStatus(EVENT_ID, USER_ID);

		assertThat(status.status()).isEqualTo("NOT_IN_QUEUE");
		assertThat(status.position()).isZero();
	}

	@Test
	void dequeue_shouldReturnUsersAndSetTurnReady() {
		when(listOps.rightPop(QUEUE_KEY)).thenReturn("user1").thenReturn("user2").thenReturn(null);
		when(setOps.remove(MEMBERS_KEY, "user1")).thenReturn(1L);
		when(setOps.remove(MEMBERS_KEY, "user2")).thenReturn(1L);
		doNothing().when(hashOps).put("queue:turn:event-123", "user1", "true");
		doNothing().when(hashOps).put("queue:turn:event-123", "user2", "true");

		List<String> users = queueService.dequeue(EVENT_ID, 5);

		assertThat(users).containsExactly("user1", "user2");
		verify(listOps, times(3)).rightPop(QUEUE_KEY);
	}

	@Test
	void isTurnReady_shouldDelegateToRedis() {
		String turnKey = "queue:turn:event-123";
		when(hashOps.hasKey(turnKey, USER_ID)).thenReturn(true);

		assertThat(queueService.isTurnReady(EVENT_ID, USER_ID)).isTrue();
		verify(hashOps).hasKey(turnKey, USER_ID);
	}

	@Test
	void clearTurnReady_shouldDeleteFromRedis() {
		String turnKey = "queue:turn:event-123";

		queueService.clearTurnReady(EVENT_ID, USER_ID);

		verify(hashOps).delete(turnKey, USER_ID);
	}
}
