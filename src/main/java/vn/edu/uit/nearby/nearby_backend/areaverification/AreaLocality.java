package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.util.Optional;
import java.util.UUID;

/**
 * The public neighbourhood label of an area, never its coordinates; listing copies it
 * onto result cards (FR-DC-05, {@code listings.locality_name}).
 */
public interface AreaLocality {

	/**
	 * @return empty for an unknown area or one without a locality name yet
	 */
	Optional<String> localityNameOf(UUID areaId);

}
