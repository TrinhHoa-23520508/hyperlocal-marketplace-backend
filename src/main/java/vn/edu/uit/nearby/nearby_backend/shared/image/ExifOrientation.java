package vn.edu.uit.nearby.nearby_backend.shared.image;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Reads the EXIF orientation tag (0x0112) of a JPEG. Phone cameras store photos sideways
 * and rely on this tag; once the pipeline strips EXIF, the pixels must be turned upright.
 */
final class ExifOrientation {

	static final int NORMAL = 1;

	private static final int MARKER_PREFIX = 0xFF;

	private static final int APP1 = 0xE1;

	private static final int START_OF_SCAN = 0xDA;

	private static final int END_OF_IMAGE = 0xD9;

	private static final byte[] EXIF_HEADER = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);

	private static final int ORIENTATION_TAG = 0x0112;

	private static final int IFD_ENTRY_BYTES = 12;

	private static final int MAX_ORIENTATION = 8;

	private ExifOrientation() {
	}

	/**
	 * @return 1-8 as defined by EXIF, {@link #NORMAL} when the tag is missing or
	 * unreadable
	 */
	static int of(byte[] jpeg) {
		int position = 2;
		while (position + 4 <= jpeg.length && unsigned(jpeg[position]) == MARKER_PREFIX) {
			int marker = unsigned(jpeg[position + 1]);
			if (marker == START_OF_SCAN || marker == END_OF_IMAGE) {
				break;
			}
			int segmentLength = readShort(jpeg, position + 2, ByteOrder.BIG_ENDIAN);
			if (marker == APP1 && hasExifHeader(jpeg, position + 4)) {
				return orientationInTiff(jpeg, position + 4 + EXIF_HEADER.length);
			}
			position += 2 + segmentLength;
		}
		return NORMAL;
	}

	private static int orientationInTiff(byte[] data, int tiffStart) {
		if (tiffStart + 8 > data.length) {
			return NORMAL;
		}
		ByteOrder order = data[tiffStart] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
		long firstDirectory = tiffStart + readInt(data, tiffStart + 4, order);
		if (firstDirectory + 2 > data.length) {
			return NORMAL;
		}
		int entries = readShort(data, (int) firstDirectory, order);
		for (int index = 0; index < entries; index++) {
			int entry = (int) firstDirectory + 2 + index * IFD_ENTRY_BYTES;
			if (entry + IFD_ENTRY_BYTES > data.length) {
				return NORMAL;
			}
			if (readShort(data, entry, order) == ORIENTATION_TAG) {
				int orientation = readShort(data, entry + 8, order);
				return orientation >= NORMAL && orientation <= MAX_ORIENTATION ? orientation : NORMAL;
			}
		}
		return NORMAL;
	}

	private static boolean hasExifHeader(byte[] data, int start) {
		if (start + EXIF_HEADER.length > data.length) {
			return false;
		}
		for (int index = 0; index < EXIF_HEADER.length; index++) {
			if (data[start + index] != EXIF_HEADER[index]) {
				return false;
			}
		}
		return true;
	}

	private static int readShort(byte[] data, int at, ByteOrder order) {
		int first = unsigned(data[at]);
		int second = unsigned(data[at + 1]);
		return order == ByteOrder.BIG_ENDIAN ? (first << 8) | second : (second << 8) | first;
	}

	private static long readInt(byte[] data, int at, ByteOrder order) {
		long high = readShort(data, order == ByteOrder.BIG_ENDIAN ? at : at + 2, order);
		long low = readShort(data, order == ByteOrder.BIG_ENDIAN ? at + 2 : at, order);
		return (high << 16) | low;
	}

	private static int unsigned(byte value) {
		return value & 0xFF;
	}

}
