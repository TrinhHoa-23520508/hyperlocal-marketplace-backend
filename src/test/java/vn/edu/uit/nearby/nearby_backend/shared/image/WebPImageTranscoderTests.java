package vn.edu.uit.nearby.nearby_backend.shared.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import vn.edu.uit.nearby.nearby_backend.support.TestImages;
import vn.edu.uit.nearby.nearby_backend.shared.image.ImageTranscoder.TranscodedImage;

/**
 * UC-SY-10 with the real native WebP encoder.
 */
class WebPImageTranscoderTests {

	private final WebPImageTranscoder transcoder = new WebPImageTranscoder();

	@Test
	@DisplayName("FR-LS-02: a 4000 x 3000 JPEG gives 1080 px and 320 px WebP derivatives")
	void makesTwoWebPDerivatives() throws IOException {
		TranscodedImage transcoded = transcoder.transcode(TestImages.jpeg(4000, 3000), ImageFormat.JPEG);

		assertThat(transcoded.width()).isEqualTo(4000);
		assertThat(transcoded.height()).isEqualTo(3000);
		assertWebP(transcoded.large(), 1080, 810);
		assertWebP(transcoded.thumbnail(), 320, 240);
	}

	@Test
	void keepsTheLongerSideOfAPortraitWithinTheLimit() throws IOException {
		TranscodedImage transcoded = transcoder.transcode(TestImages.jpeg(1500, 3000), ImageFormat.JPEG);

		assertWebP(transcoded.large(), 540, 1080);
		assertWebP(transcoded.thumbnail(), 160, 320);
	}

	@Test
	@DisplayName("UC-SY-10: a small image is not enlarged")
	void doesNotEnlargeASmallImage() throws IOException {
		TranscodedImage transcoded = transcoder.transcode(TestImages.png(200, 100), ImageFormat.PNG);

		assertWebP(transcoded.large(), 200, 100);
		assertWebP(transcoded.thumbnail(), 200, 100);
	}

	@Test
	void transcodesAWebPOriginal() throws IOException {
		byte[] webp = transcoder.transcode(TestImages.jpeg(1200, 900), ImageFormat.JPEG).large();

		TranscodedImage transcoded = transcoder.transcode(webp, ImageFormat.WEBP);

		assertWebP(transcoded.thumbnail(), 320, 240);
	}

	@ParameterizedTest(name = "orientation {0} -> {1} x {2}")
	@CsvSource({ "1, 400, 300", "3, 400, 300", "6, 300, 400", "8, 300, 400", "5, 300, 400", "2, 400, 300" })
	@DisplayName("FR-LS-02: the EXIF orientation is applied before the metadata is dropped")
	void turnsThePictureUpright(int orientation, int width, int height) throws IOException {
		byte[] jpeg = TestImages.jpegWithExif(400, 300, orientation, ByteOrder.BIG_ENDIAN);

		TranscodedImage transcoded = transcoder.transcode(jpeg, ImageFormat.JPEG);

		assertThat(transcoded.width()).isEqualTo(width);
		assertWebP(transcoded.large(), width, height);
	}

	@Test
	@DisplayName("NFR-31: no derivative carries EXIF or GPS data")
	void dropsTheExifSegment() {
		byte[] jpeg = TestImages.jpegWithExif(800, 600, 6, ByteOrder.LITTLE_ENDIAN);

		TranscodedImage transcoded = transcoder.transcode(jpeg, ImageFormat.JPEG);

		for (byte[] derivative : new byte[][] { transcoded.large(), transcoded.thumbnail() }) {
			String content = new String(derivative, StandardCharsets.ISO_8859_1);
			assertThat(content).doesNotContain("EXIF").doesNotContain("Exif").doesNotContain("GPS");
		}
	}

	@Test
	@DisplayName("FR-LS-02: content that cannot be decoded is DECODE_ERROR")
	void rejectsUndecodableContent() {
		byte[] corrupt = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3, 4, 5, 6, 7, 8 };

		assertRejected(() -> transcoder.transcode(corrupt, ImageFormat.JPEG), ImageRejectReason.DECODE_ERROR);
	}

	@Test
	@DisplayName("FR-LS-02: a PNG declaring 10 000 x 10 000 pixels is TOO_LARGE before any pixel is decoded")
	void rejectsADecompressionBomb() {
		assertRejected(() -> transcoder.transcode(TestImages.pngHeaderOnly(10_000, 10_000), ImageFormat.PNG),
				ImageRejectReason.TOO_LARGE);
	}

	private static void assertWebP(byte[] content, int width, int height) throws IOException {
		assertThat(ImageFormat.detect(content)).hasValue(ImageFormat.WEBP);
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(content));
		assertThat(decoded.getWidth()).isEqualTo(width);
		assertThat(decoded.getHeight()).isEqualTo(height);
	}

	private static void assertRejected(Runnable transcoding, ImageRejectReason reason) {
		assertThatThrownBy(transcoding::run).isInstanceOf(UnusableImageException.class)
			.extracting(ex -> ((UnusableImageException) ex).reason())
			.isEqualTo(reason);
	}

}
