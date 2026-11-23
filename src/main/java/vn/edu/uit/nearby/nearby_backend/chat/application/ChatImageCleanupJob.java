package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository.ChatImageRow;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;
import vn.edu.uit.nearby.nearby_backend.shared.storage.StorageException;

/**
 * UC-SY-15 hourly, for chat: images not used by a message {@code upload.orphan_ttl_hours}
 * after their upload started are deleted with their objects, so an unused photo is gone
 * within 25 hours (FR-CH-04, NFR-31). Safe to run twice; an image whose objects cannot be
 * deleted now stays for the next run.
 */
@Slf4j
@Component
public class ChatImageCleanupJob {

	static final String HOURLY = "0 0 * * * *";

	static final String VIETNAM_ZONE = "Asia/Ho_Chi_Minh";

	static final int BATCH_SIZE = 200;

	private final ChatImageRepository images;

	private final ImageStorage storage;

	private final ChatParameters parameters;

	private final Clock clock;

	ChatImageCleanupJob(ChatImageRepository images, ImageStorage storage, ChatParameters parameters, Clock clock) {
		this.images = images;
		this.storage = storage;
		this.parameters = parameters;
		this.clock = clock;
	}

	@Transactional
	@Scheduled(cron = HOURLY, zone = VIETNAM_ZONE)
	public void run() {
		runAt(clock.instant());
	}

	/**
	 * @return how many orphans were deleted
	 */
	@Transactional
	public int runAt(Instant now) {
		Instant createdBefore = now.minus(parameters.orphanImageTtl());
		int deleted = 0;
		List<ChatImageRow> batch;
		do {
			batch = images.findOrphans(createdBefore, BATCH_SIZE);
			List<UUID> removable = batch.stream().filter(this::deleteObjects).map(ChatImageRow::id).toList();
			images.delete(removable);
			deleted += removable.size();
			if (removable.size() < batch.size()) {
				break;
			}
		}
		while (batch.size() == BATCH_SIZE);
		return deleted;
	}

	private boolean deleteObjects(ChatImageRow image) {
		try {
			Stream.of(image.originalKey(), image.thumbKey(), image.largeKey())
				.filter(key -> key != null)
				.forEach(storage::delete);
			return true;
		}
		catch (StorageException ex) {
			log.warn("Orphan chat image {} kept for the next run: {}", image.id(), ex.getMessage());
			return false;
		}
	}

}
