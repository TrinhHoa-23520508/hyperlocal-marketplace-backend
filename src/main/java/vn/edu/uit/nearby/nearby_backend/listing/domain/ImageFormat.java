package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Image types FR-LS-02 accepts, recognised from their magic bytes rather than from what
 * the client declared.
 */
public enum ImageFormat {

	JPEG("image/jpeg", true), PNG("image/png", true), WEBP("image/webp", true),

	/**
	 * Accepted by the SRS but not decoded in R1 (DEC-LS-4): the Android app converts HEIC
	 * to JPEG before uploading.
	 */
	HEIC("image/heic", false);

	private static final byte[] JPEG_SIGNATURE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	private static final int HEADER_BYTES = 12;

	private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1",
			"msf1");

	private final String mimeType;

	private final boolean decodable;

	ImageFormat(String mimeType, boolean decodable) {
		this.mimeType = mimeType;
		this.decodable = decodable;
	}

	public String mimeType() {
		return mimeType;
	}

	public boolean isDecodable() {
		return decodable;
	}

	/**
	 * @return empty when the bytes are not one of the accepted types
	 */
	public static Optional<ImageFormat> detect(byte[] content) {
		if (startsWith(content, JPEG_SIGNATURE)) {
			return Optional.of(JPEG);
		}
		if (startsWith(content, PNG_SIGNATURE)) {
			return Optional.of(PNG);
		}
		if (content.length < HEADER_BYTES) {
			return Optional.empty();
		}
		if (ascii(content, 0, 4).equals("RIFF") && ascii(content, 8, 12).equals("WEBP")) {
			return Optional.of(WEBP);
		}
		if (ascii(content, 4, 8).equals("ftyp") && HEIC_BRANDS.contains(ascii(content, 8, 12))) {
			return Optional.of(HEIC);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] content, byte[] signature) {
		return content.length >= signature.length
				&& Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
	}

	private static String ascii(byte[] content, int from, int to) {
		return new String(content, from, to - from, StandardCharsets.US_ASCII);
	}

}
