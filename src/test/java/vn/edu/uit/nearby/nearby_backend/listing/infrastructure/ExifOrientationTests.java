package vn.edu.uit.nearby.nearby_backend.listing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vn.edu.uit.nearby.nearby_backend.listing.TestImages;

class ExifOrientationTests {

	@ParameterizedTest
	@ValueSource(ints = { 1, 3, 6, 8 })
	void readsABigEndianOrientation(int orientation) {
		assertThat(ExifOrientation.of(TestImages.jpegWithExif(40, 30, orientation, ByteOrder.BIG_ENDIAN)))
			.isEqualTo(orientation);
	}

	@ParameterizedTest
	@ValueSource(ints = { 2, 5, 7 })
	void readsALittleEndianOrientation(int orientation) {
		assertThat(ExifOrientation.of(TestImages.jpegWithExif(40, 30, orientation, ByteOrder.LITTLE_ENDIAN)))
			.isEqualTo(orientation);
	}

	@Test
	void isNormalWithoutExif() {
		assertThat(ExifOrientation.of(TestImages.jpeg(40, 30))).isEqualTo(ExifOrientation.NORMAL);
	}

	@Test
	void isNormalForAnOutOfRangeValue() {
		assertThat(ExifOrientation.of(TestImages.jpegWithExif(40, 30, 42, ByteOrder.BIG_ENDIAN)))
			.isEqualTo(ExifOrientation.NORMAL);
	}

	@Test
	void isNormalForTruncatedData() {
		byte[] truncated = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 8, 'E', 'x', 'i', 'f', 0, 0 };

		assertThat(ExifOrientation.of(truncated)).isEqualTo(ExifOrientation.NORMAL);
	}

}
