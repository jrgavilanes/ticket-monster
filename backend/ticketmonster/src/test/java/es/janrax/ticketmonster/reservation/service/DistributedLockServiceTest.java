package es.janrax.ticketmonster.reservation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOps;

	@InjectMocks
	private DistributedLockService lockService;

	private static final String EVENT_ID = "event-123";
	private static final String ZONE_ID = "zone-campo";
	private static final String USER_ID = "user-456";
	private static final String LOCK_KEY = "reservation:event-123:zone-campo:user-456";

	@Test
	void acquireLock_shouldReturnTrue_whenSetSucceeds() {
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(valueOps.setIfAbsent(LOCK_KEY, USER_ID, Duration.ofSeconds(600))).thenReturn(true);

		boolean result = lockService.acquireLock(EVENT_ID, ZONE_ID, USER_ID, 600);

		assertThat(result).isTrue();
		verify(valueOps).setIfAbsent(LOCK_KEY, USER_ID, Duration.ofSeconds(600));
	}

	@Test
	void acquireLock_shouldReturnFalse_whenKeyExists() {
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(valueOps.setIfAbsent(LOCK_KEY, USER_ID, Duration.ofSeconds(600))).thenReturn(false);

		boolean result = lockService.acquireLock(EVENT_ID, ZONE_ID, USER_ID, 600);

		assertThat(result).isFalse();
	}

	@Test
	void releaseLock_shouldDeleteKey() {
		lockService.releaseLock(EVENT_ID, ZONE_ID, USER_ID);

		verify(redisTemplate).delete(LOCK_KEY);
	}

	@Test
	void isLocked_shouldReturnTrue_whenKeyExists() {
		when(redisTemplate.hasKey(LOCK_KEY)).thenReturn(true);

		assertThat(lockService.isLocked(EVENT_ID, ZONE_ID, USER_ID)).isTrue();
	}

	@Test
	void isLocked_shouldReturnFalse_whenKeyDoesNotExist() {
		when(redisTemplate.hasKey(LOCK_KEY)).thenReturn(false);

		assertThat(lockService.isLocked(EVENT_ID, ZONE_ID, USER_ID)).isFalse();
	}
}
