package vn.edu.uit.nearby.nearby_backend.listing;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

/**
 * Real image bytes for the image pipeline tests.
 */
public final class TestImages {

	private TestImages() {
	}

	public static byte[] jpeg(int width, int height) {
		return encode(picture(width, height, BufferedImage.TYPE_INT_RGB), "jpg");
	}

	public static byte[] png(int width, int height) {
		return encode(picture(width, height, BufferedImage.TYPE_INT_ARGB), "png");
	}

	/**
	 * A JPEG carrying an EXIF APP1 segment with the orientation tag and a GPS-looking
	 * marker string, as a phone camera writes it.
	 */
	public static byte[] jpegWithExif(int width, int height, int orientation, ByteOrder order) {
		byte[] jpeg = jpeg(width, height);
		byte[] app1 = exifSegment(orientation, order);
		byte[] result = new byte[jpeg.length + app1.length];
		System.arraycopy(jpeg, 0, result, 0, 2);
		System.arraycopy(app1, 0, result, 2, app1.length);
		System.arraycopy(jpeg, 2, result, 2 + app1.length, jpeg.length - 2);
		return result;
	}

	/**
	 * A PNG whose header declares the given size but carries no pixel data: enough for a
	 * reader to report the dimensions.
	 */
	public static byte[] pngHeaderOnly(int width, int height) {
		ByteBuffer header = ByteBuffer.allocate(8 + 25).order(ByteOrder.BIG_ENDIAN);
		header.put(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
		byte[] ihdr = ByteBuffer.allocate(17)
			.put("IHDR".getBytes(StandardCharsets.US_ASCII))
			.putInt(width)
			.putInt(height)
			.put(new byte[] { 8, 6, 0, 0, 0 })
			.array();
		CRC32 crc = new CRC32();
		crc.update(ihdr);
		header.putInt(13).put(ihdr).putInt((int) crc.getValue());
		return header.array();
	}

	private static BufferedImage picture(int width, int height, int type) {
		BufferedImage image = new BufferedImage(width, height, type);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.ORANGE);
		graphics.fillRect(0, 0, width, height);
		graphics.setColor(Color.BLUE);
		graphics.fillRect(0, 0, width / 2, height / 4);
		graphics.dispose();
		return image;
	}

	private static byte[] encode(BufferedImage image, String format) {
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(image, format, output);
			return output.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static byte[] exifSegment(int orientation, ByteOrder order) {
		ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4 + 16).order(order);
		tiff.put(order == ByteOrder.LITTLE_ENDIAN ? new byte[] { 'I', 'I' } : new byte[] { 'M', 'M' });
		tiff.putShort((short) 42).putInt(8);
		tiff.putShort((short) 1);
		tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
		tiff.putInt(0);
		tiff.put("GPS 10.87,106.80".getBytes(StandardCharsets.US_ASCII));
		byte[] exifHeader = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
		int length = 2 + exifHeader.length + tiff.capacity();
		return ByteBuffer.allocate(2 + length)
			.order(ByteOrder.BIG_ENDIAN)
			.put((byte) 0xFF)
			.put((byte) 0xE1)
			.putShort((short) length)
			.put(exifHeader)
			.put(tiff.array())
			.array();
	}

}
