package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import vn.edu.uit.nearby.nearby_backend.chat.application.ChatImageService.ChatImageUploaded;

/**
 * Runs UC-SY-10 for chat images once upload-complete has committed. Same shape as the
 * listing queue: a small private bounded pool, and the caller does the work itself when
 * the queue is full, which slows uploads down instead of losing them.
 */
@Slf4j
@Component
class ChatImageQueue {

	private static final int THREADS = 2;

	private static final int QUEUE_CAPACITY = 100;

	private static final long SHUTDOWN_GRACE_SECONDS = 30;

	private final ChatImageProcessor processor;

	private final ExecutorService executor = new ThreadPoolExecutor(THREADS, THREADS, 0, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(QUEUE_CAPACITY), Thread.ofPlatform().name("chat-image-", 0).factory(),
			new ThreadPoolExecutor.CallerRunsPolicy());

	ChatImageQueue(ChatImageProcessor processor) {
		this.processor = processor;
	}

	@TransactionalEventListener
	void onImageUploaded(ChatImageUploaded event) {
		executor.execute(() -> processQuietly(event));
	}

	@PreDestroy
	void drain() throws InterruptedException {
		executor.shutdown();
		if (!executor.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
			log.warn("Chat image processing did not finish before shutdown");
		}
	}

	/**
	 * An unexpected failure leaves the image UPLOADING; the app can call upload-complete
	 * again, and the orphan cleanup removes it if it is never used.
	 */
	private void processQuietly(ChatImageUploaded event) {
		try {
			processor.process(event.imageId());
		}
		catch (RuntimeException ex) {
			log.error("Processing chat image {} failed", event.imageId(), ex);
		}
	}

}
