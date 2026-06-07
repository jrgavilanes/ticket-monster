package es.janrax.ticketmonster.reservation.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class DistributedLockService {

	private static final String LOCK_PREFIX = "reservation:";

	private final StringRedisTemplate redisTemplate;

	public DistributedLockService(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public boolean acquireLock(String eventId, String zoneId, String userId, int ttlSeconds) {
		String key = LOCK_PREFIX + eventId + ":" + zoneId + ":" + userId;
		Boolean result = redisTemplate.opsForValue().setIfAbsent(key, userId, Duration.ofSeconds(ttlSeconds));
		return Boolean.TRUE.equals(result);
	}

	public void releaseLock(String eventId, String zoneId, String userId) {
		String key = LOCK_PREFIX + eventId + ":" + zoneId + ":" + userId;
		redisTemplate.delete(key);
	}

	public boolean isLocked(String eventId, String zoneId, String userId) {
		String key = LOCK_PREFIX + eventId + ":" + zoneId + ":" + userId;
		return Boolean.TRUE.equals(redisTemplate.hasKey(key));
	}
}
