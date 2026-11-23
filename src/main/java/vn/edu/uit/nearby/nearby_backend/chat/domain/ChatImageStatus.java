package vn.edu.uit.nearby.nearby_backend.chat.domain;

/**
 * {@code chat_images.processing_status}: UPLOADING until the pipeline (UC-SY-10) has
 * produced the two WebP derivatives or refused the upload.
 */
public enum ChatImageStatus {

	UPLOADING, READY, REJECTED

}
