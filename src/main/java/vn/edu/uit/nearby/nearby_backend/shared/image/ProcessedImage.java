package vn.edu.uit.nearby.nearby_backend.shared.image;

/**
 * The output of UC-SY-10 for one upload: the two metadata-free WebP derivatives and the
 * facts verified on the original.
 *
 * @param mimeType type verified from the magic bytes
 */
public record ProcessedImage(String thumbKey, String largeKey, String mimeType, int sizeBytes, int width, int height) {

}
