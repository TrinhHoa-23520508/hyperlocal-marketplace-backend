package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.UUID;

/**
 * Implemented by the area verification module, so the trading gate can read a user's
 * areas without identity depending on that module (which would create a module cycle).
 * Without an implementation the gate treats every user as having no area.
 */
public interface TradingAreaStatus {

	AreaStanding standingOf(UUID userId);

}
