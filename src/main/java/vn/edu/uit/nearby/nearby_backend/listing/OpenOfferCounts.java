package vn.edu.uit.nearby.nearby_backend.listing;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * SPI the transaction module implements (register LS-W6): the open-offer counter of "Tin
 * của tôi" (FR-LS-11). Without an implementation every count is 0.
 */
public interface OpenOfferCounts {

	/**
	 * @return OFFERED transactions per listing; listings without any may be absent
	 */
	Map<UUID, Integer> countOpenOffers(Collection<UUID> listingIds);

}
