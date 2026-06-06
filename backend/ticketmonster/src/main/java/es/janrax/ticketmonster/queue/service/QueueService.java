package es.janrax.ticketmonster.queue.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class QueueService {

	private static final String QUEUE_PREFIX = "queue:";
	private static final String MEMBERS_PREFIX = "queue:members:";
	private static final String TURN_READY_PREFIX = "queue:turn:";

	private final StringRedisTemplate redisTemplate;

	public QueueService(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public QueueTicket join(String eventId, String userId) {
		String membersKey = MEMBERS_PREFIX + eventId;
		if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(membersKey, userId))) {
			Long position = getPosition(eventId, userId);
			String existingTicket = (String) redisTemplate.opsForHash().get(membersKey + ":tickets", userId);
			return new QueueTicket(existingTicket != null ? existingTicket : "existing", position != null ? position : 0L);
		}

		String ticketId = UUID.randomUUID().toString();
		String queueKey = QUEUE_PREFIX + eventId;

		redisTemplate.opsForList().leftPush(queueKey, userId);
		redisTemplate.opsForSet().add(membersKey, userId);
		redisTemplate.opsForHash().put(membersKey + ":tickets", userId, ticketId);

		Long position = redisTemplate.opsForList().size(queueKey);
		return new QueueTicket(ticketId, position != null ? position : 1L);
	}

	public Long getPosition(String eventId, String userId) {
		String queueKey = QUEUE_PREFIX + eventId;
		List<String> members = redisTemplate.opsForList().range(queueKey, 0, -1);
		if (members == null) return null;
		int index = members.indexOf(userId);
		return index >= 0 ? (long) (index + 1) : null;
	}

	public QueueStatus getStatus(String eventId, String userId) {
		if (isTurnReady(eventId, userId)) {
			return new QueueStatus("TURN_READY", 0L);
		}
		Long position = getPosition(eventId, userId);
		if (position == null) {
			return new QueueStatus("NOT_IN_QUEUE", 0L);
		}
		return new QueueStatus("WAITING", position);
	}

	public List<String> dequeue(String eventId, int count) {
		String queueKey = QUEUE_PREFIX + eventId;
		List<String> users = new java.util.ArrayList<>();
		for (int i = 0; i < count; i++) {
			String userId = redisTemplate.opsForList().rightPop(queueKey);
			if (userId == null) break;
			users.add(userId);
			redisTemplate.opsForSet().remove(MEMBERS_PREFIX + eventId, userId);
			redisTemplate.opsForHash().put(TURN_READY_PREFIX + eventId, userId, "true");
		}
		return users;
	}

	public boolean isTurnReady(String eventId, String userId) {
		return Boolean.TRUE.equals(
				redisTemplate.opsForHash().hasKey(TURN_READY_PREFIX + eventId, userId)
		);
	}

	public void clearTurnReady(String eventId, String userId) {
		redisTemplate.opsForHash().delete(TURN_READY_PREFIX + eventId, userId);
	}

	public record QueueTicket(String ticketId, long position) {}
	public record QueueStatus(String status, long position) {}
}
