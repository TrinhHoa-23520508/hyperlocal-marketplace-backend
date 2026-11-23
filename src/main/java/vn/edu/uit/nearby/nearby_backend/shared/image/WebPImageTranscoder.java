package vn.edu.uit.nearby.nearby_backend.shared.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

/**
 * DEC-LS-4: decodes JPEG (TwelveMonkeys), PNG (JDK) and WebP (libwebp), turns the picture
 * upright, and re-encodes lossy WebP at two sizes. Encoding from pixels writes no
 * metadata, so EXIF/GPS never reaches a derivative (NFR-31). Sizes bound the longer side;
 * smaller images are not enlarged.
 */
@Component
class WebPImageTranscoder implements ImageTranscoder {

	/**
	 * Guards memory against decompression bombs: a 5 MB PNG can declare billions of
	 * pixels.
	 */
	static final long MAX_SOURCE_PIXELS = 50_000_000L;

	private static final String WEBP_MIME_TYPE = "image/webp";

	private static final String LOSSY = "Lossy";

	private static final float WEBP_QUALITY = 0.8f;

	@Override
	public TranscodedImage transcode(byte[] original, ImageFormat format) {
		BufferedImage upright = orient(decode(original),
				format == ImageFormat.JPEG ? ExifOrientation.of(original) : ExifOrientation.NORMAL);
		BufferedImage large = fitWithin(upright, LARGE_PX);
		BufferedImage thumbnail = fitWithin(large, THUMBNAIL_PX);
		return new TranscodedImage(upright.getWidth(), upright.getHeight(), encodeWebP(thumbnail), encodeWebP(large));
	}

	private static BufferedImage decode(byte[] original) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new UnusableImageException(ImageRejectReason.DECODE_ERROR, "No decoder for the image.");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
				if (pixels > MAX_SOURCE_PIXELS) {
					throw new UnusableImageException(ImageRejectReason.TOO_LARGE,
							"The image has " + pixels + " pixels; at most " + MAX_SOURCE_PIXELS + " are decoded.");
				}
				ImageReadParam param = reader.getDefaultReadParam();
				int step = subsamplingStep(Math.max(reader.getWidth(0), reader.getHeight(0)));
				param.setSourceSubsampling(step, step, 0, 0);
				return reader.read(0, param);
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof UnusableImageException unusable) {
				throw unusable;
			}
			throw new UnusableImageException(ImageRejectReason.DECODE_ERROR, "The image cannot be decoded.", ex);
		}
	}

	/**
	 * Skips source pixels while the result stays at least twice the large size, which
	 * keeps memory low without losing quality.
	 */
	private static int subsamplingStep(int longerSide) {
		return Math.max(1, longerSide / (2 * LARGE_PX));
	}

	private static BufferedImage orient(BufferedImage image, int exifOrientation) {
		if (exifOrientation == ExifOrientation.NORMAL) {
			return image;
		}
		int width = image.getWidth();
		int height = image.getHeight();
		boolean quarterTurn = exifOrientation >= 5;
		AffineTransform transform = switch (exifOrientation) {
			case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
			case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
			case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
			case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
			case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
			case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
			default -> new AffineTransform(0, -1, 1, 0, 0, width);
		};
		BufferedImage oriented = new BufferedImage(quarterTurn ? height : width, quarterTurn ? width : height,
				pixelTypeOf(image));
		Graphics2D graphics = oriented.createGraphics();
		try {
			graphics.drawImage(image, transform, null);
		}
		finally {
			graphics.dispose();
		}
		return oriented;
	}

	/**
	 * Scales so the longer side is at most {@code maxSide}, halving step by step for a
	 * sharper result than one bilinear pass.
	 */
	private static BufferedImage fitWithin(BufferedImage image, int maxSide) {
		BufferedImage current = image;
		int targetWidth = image.getWidth();
		int targetHeight = image.getHeight();
		int longerSide = Math.max(targetWidth, targetHeight);
		if (longerSide > maxSide) {
			targetWidth = Math.max(1, Math.round((float) targetWidth * maxSide / longerSide));
			targetHeight = Math.max(1, Math.round((float) targetHeight * maxSide / longerSide));
		}
		do {
			int width = Math.max(targetWidth, current.getWidth() / 2);
			int height = Math.max(targetHeight, current.getHeight() / 2);
			current = redraw(current, width, height);
		}
		while (current.getWidth() != targetWidth || current.getHeight() != targetHeight);
		return current;
	}

	private static BufferedImage redraw(BufferedImage image, int width, int height) {
		BufferedImage scaled = new BufferedImage(width, height, pixelTypeOf(image));
		Graphics2D graphics = scaled.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(image, 0, 0, width, height, null);
		}
		finally {
			graphics.dispose();
		}
		return scaled;
	}

	private static int pixelTypeOf(BufferedImage image) {
		return image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
	}

	private static byte[] encodeWebP(BufferedImage image) {
		ImageWriter writer = ImageIO.getImageWritersByMIMEType(WEBP_MIME_TYPE).next();
		ByteArrayOutputStream encoded = new ByteArrayOutputStream();
		try (ImageOutputStream output = ImageIO.createImageOutputStream(encoded)) {
			writer.setOutput(output);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionType(LOSSY);
			param.setCompressionQuality(WEBP_QUALITY);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		catch (IOException ex) {
			throw new IllegalStateException("WebP encoding failed", ex);
		}
		finally {
			writer.dispose();
		}
		return encoded.toByteArray();
	}

}
