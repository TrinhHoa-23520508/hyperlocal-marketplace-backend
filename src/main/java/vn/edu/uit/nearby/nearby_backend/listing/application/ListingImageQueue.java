package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingImageService.ListingImageUploaded;

/**
 * Runs UC-SY-10 once upload-complete has committed. Processing is CPU-bound, so it gets a
 * small bounded pool of its own instead of request threads; when the queue is full the
 * caller processes the image itself, which slows uploads down instead of losing them. The
 * pool is private so it never replaces the application's default task executor.
 */
@Slf4j
@Component
class ListingImageQueue {

	private static final int THREADS = 2;

	private static final int QUEUE_CAPACITY = 100;

	private static final long SHUTDOWN_GRACE_SECONDS = 30;

	private final ListingImageProcessor processor;

	private final ExecutorService executor = new ThreadPoolExecutor(THREADS, THREADS, 0, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(QUEUE_CAPACITY), Thread.ofPlatform().name("listing-image-", 0).factory(),
			new ThreadPoolExecutor.CallerRunsPolicy());

	ListingImageQueue(ListingImageProcessor processor) {
		this.processor = processor;
	}

	@TransactionalEventListener
	void onImageUploaded(ListingImageUploaded event) {
		executor.execute(() -> processQuietly(event));
	}

	@PreDestroy
	void drain() throws InterruptedException {
		executor.shutdown();
		if (!executor.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
			log.warn("Listing image processing did not finish before shutdown");
		}
	}

	/**
	 * An unexpected failure leaves the image UPLOADING; the app can call upload-complete
	 * again, and the orphan cleanup removes it if it is never used.
	 */
	private void processQuietly(ListingImageUploaded event) {
		try {
			processor.process(event.imageId());
		}
		catch (RuntimeException ex) {
			log.error("Processing listing image {} failed", event.imageId(), ex);
		}
	}

}
