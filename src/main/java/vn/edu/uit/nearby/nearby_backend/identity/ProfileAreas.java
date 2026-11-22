package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by the area verification module, so profiles can show a user's areas
 * without identity depending on that module (which would create a module cycle). Without
 * an implementation profiles show no area.
 */
public interface ProfileAreas {

	/**
	 * The owner's areas that are not removed, in slot order (FR-ID-12).
	 */
	List<ProfileArea> areasOf(UUID userId);

	/**
	 * The public neighborhood name of the user's area, never a coordinate (FR-ID-13).
	 */
	Optional<String> publicLocalityOf(UUID userId);

}
