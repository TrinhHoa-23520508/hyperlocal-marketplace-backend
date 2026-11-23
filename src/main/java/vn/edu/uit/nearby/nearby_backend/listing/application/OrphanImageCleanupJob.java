package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingImage;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;
import vn.edu.uit.nearby.nearby_backend.shared.storage.StorageException;

/**
 * UC-SY-15 hourly: images not attached to a listing {@code upload.orphan_ttl_hours} after
 * their upload started are deleted with their objects, so an orphan is gone within 25
 * hours (FR-LS-02). Images of deleted listings were detached and go the same way. Safe to
 * run twice; an image whose objects cannot be deleted now stays for the next run.
 */
@Slf4j
@Component
public class OrphanImageCleanupJob {

	static final String HOURLY = "0 0 * * * *";

	static final String VIETNAM_ZONE = "Asia/Ho_Chi_Minh";

	static final int BATCH_SIZE = 200;

	private final ListingImageRepository images;

	private final ImageStorage storage;

	private final ListingParameters parameters;

	private final Clock clock;

	OrphanImageCleanupJob(ListingImageRepository images, ImageStorage storage, ListingParameters parameters,
			Clock clock) {
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
		List<ListingImage> batch;
		do {
			batch = images.findOrphans(createdBefore, Limit.of(BATCH_SIZE));
			List<ListingImage> removable = batch.stream().filter(this::deleteObjects).toList();
			images.deleteAll(removable);
			images.flush();
			deleted += removable.size();
			if (removable.size() < batch.size()) {
				break;
			}
		}
		while (batch.size() == BATCH_SIZE);
		return deleted;
	}

	private boolean deleteObjects(ListingImage image) {
		try {
			Stream.of(image.getOriginalKey(), image.getThumbKey(), image.getLargeKey())
				.filter(key -> key != null)
				.forEach(storage::delete);
			return true;
		}
		catch (StorageException ex) {
			log.warn("Orphan image {} kept for the next run: {}", image.getId(), ex.getMessage());
			return false;
		}
	}

}
