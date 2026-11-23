package vn.edu.uit.nearby.nearby_backend.listing;

import java.util.UUID;

/**
 * SPI the transaction module implements (register LS-W1): the daily posting limit of
 * FR-LS-04 (BR-07) applies only until the seller's first completed transaction. Without
 * an implementation no seller has completed one.
 */
public interface SellerTradeHistory {

	boolean hasCompletedTransaction(UUID sellerId);

}
