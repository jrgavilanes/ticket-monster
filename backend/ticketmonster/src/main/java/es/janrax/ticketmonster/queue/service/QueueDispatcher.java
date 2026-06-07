package es.janrax.ticketmonster.queue.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class QueueDispatcher {

	private static final Logger log = LoggerFactory.getLogger(QueueDispatcher.class);

	private final QueueService queueService;
	private final StringRedisTemplate redisTemplate;

	@Value("${ticketmonster.queue.batch-size:500}")
	private int batchSize;

	public QueueDispatcher(QueueService queueService, StringRedisTemplate redisTemplate) {
		this.queueService = queueService;
		this.redisTemplate = redisTemplate;
	}

	@Scheduled(fixedDelayString = "${ticketmonster.queue.batch-interval-ms:2000}")
	public void dispatchBatch() {
		Set<String> queueKeys = redisTemplate.keys("queue:turn:*");
		if (queueKeys == null) return;

		Set<String> allQueueKeys = redisTemplate.keys("queue:*");
		if (allQueueKeys == null) return;

		for (String key : allQueueKeys) {
			if (key.startsWith("queue:members:") || key.startsWith("queue:turn:")) continue;
			String eventId = key.substring("queue:".length());

			Long queueSize = redisTemplate.opsForList().size(key);
			if (queueSize != null && queueSize > 0) {
				List<String> users = queueService.dequeue(eventId, batchSize);
				if (!users.isEmpty()) {
					log.info("Dispatched batch of {} users for event {}", users.size(), eventId);
				}
			}
		}
	}
}
