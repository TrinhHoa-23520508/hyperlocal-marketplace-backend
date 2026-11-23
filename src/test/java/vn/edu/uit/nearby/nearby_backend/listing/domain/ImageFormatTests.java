package vn.edu.uit.nearby.nearby_backend.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.listing.TestImages;

/**
 * FR-LS-02: the type comes from the magic bytes.
 */
class ImageFormatTests {

	@Test
	void recognisesJpegAndPng() {
		assertThat(ImageFormat.detect(TestImages.jpeg(4, 4))).hasValue(ImageFormat.JPEG);
		assertThat(ImageFormat.detect(TestImages.png(4, 4))).hasValue(ImageFormat.PNG);
	}

	@Test
	void recognisesWebP() {
		assertThat(ImageFormat.detect(ascii("RIFF\0\0\0\0WEBPVP8 "))).hasValue(ImageFormat.WEBP);
	}

	@Test
	@DisplayName("DEC-LS-4: HEIC is recognised but not decoded")
	void recognisesHeicAsNotDecodable() {
		assertThat(ImageFormat.detect(ascii("\0\0\0\u0018ftypheic\0\0\0\0"))).hasValue(ImageFormat.HEIC);
		assertThat(ImageFormat.HEIC.isDecodable()).isFalse();
		assertThat(ImageFormat.JPEG.isDecodable()).isTrue();
	}

	@Test
	@DisplayName("FR-LS-02: a renamed text file or GIF is not an accepted image")
	void refusesOtherContent() {
		assertThat(ImageFormat.detect(ascii("GIF89a......"))).isEmpty();
		assertThat(ImageFormat.detect(ascii("hello"))).isEmpty();
		assertThat(ImageFormat.detect(new byte[0])).isEmpty();
		assertThat(ImageFormat.detect(ascii("\0\0\0\u0018ftypmp42\0\0\0\0"))).isEmpty();
	}

	@Test
	void namesTheMimeTypes() {
		assertThat(ImageFormat.WEBP.mimeType()).isEqualTo("image/webp");
		assertThat(ImageFormat.HEIC.mimeType()).isEqualTo("image/heic");
	}

	private static byte[] ascii(String text) {
		return text.getBytes(StandardCharsets.ISO_8859_1);
	}

}
