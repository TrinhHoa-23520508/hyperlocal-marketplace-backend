package vn.edu.uit.nearby.nearby_backend.listing;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI the transaction module implements (register LS-W2): FR-LS-08 refuses to delete a
 * listing while a transaction on it is OFFERED or ACCEPTED. Without an implementation no
 * listing has open transactions.
 */
public interface OpenTransactionCheck {

	/**
	 * @return an OFFERED or ACCEPTED transaction on the listing, named in the 409
	 */
	Optional<UUID> findOpenTransaction(UUID listingId);

}
