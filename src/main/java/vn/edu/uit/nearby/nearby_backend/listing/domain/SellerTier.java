package vn.edu.uit.nearby.nearby_backend.listing.domain;

/**
 * The seller's reputation tier copied onto each listing for result cards (FR-DC-05,
 * FR-RP-08; {@code listings.seller_tier}). Sellers without reviews are {@link #NEW}
 * (FR-RP-01).
 */
public enum SellerTier {

	NEW, CAUTION, STANDARD, TRUSTED, HIGHLY_TRUSTED

}
