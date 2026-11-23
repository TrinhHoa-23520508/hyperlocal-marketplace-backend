package vn.edu.uit.nearby.nearby_backend.shared.image;

import java.io.Serial;

/**
 * The image pipeline refused an upload (FR-LS-02); the image becomes REJECTED with this
 * reason.
 */
public class UnusableImageException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final ImageRejectReason reason;

	public UnusableImageException(ImageRejectReason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public UnusableImageException(ImageRejectReason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public ImageRejectReason reason() {
		return reason;
	}

}
