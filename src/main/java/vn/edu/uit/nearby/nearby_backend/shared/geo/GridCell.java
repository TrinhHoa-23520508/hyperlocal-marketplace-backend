package vn.edu.uit.nearby.nearby_backend.shared.geo;

/**
 * One square of a {@link GridSnapper} grid.
 *
 * @param code stable identifier of the square, at most 20 characters
 * @param center the center of the square, the only point that may be shown in place of
 * the precise location
 */
public record GridCell(String code, GeoPoint center) {
}
