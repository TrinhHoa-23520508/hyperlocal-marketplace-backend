package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import java.util.Collection;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Numbered places for a user's live areas, at most {@code area.max_count} (FR-AR-02).
 */
public record AreaSlots(int maxCount) {

	public AreaSlots {
		if (maxCount < 1) {
			throw new IllegalArgumentException("area.max_count must be at least 1: " + maxCount);
		}
	}

	/**
	 * @return the lowest slot not taken by a live area, empty when all are taken
	 */
	public OptionalInt firstFree(Collection<Integer> occupiedSlots) {
		return IntStream.rangeClosed(1, maxCount).filter(slot -> !occupiedSlots.contains(slot)).findFirst();
	}

}
