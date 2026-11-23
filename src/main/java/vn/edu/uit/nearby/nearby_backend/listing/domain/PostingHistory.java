package vn.edu.uit.nearby.nearby_backend.listing.domain;

/**
 * What the posting quota needs to know about a seller (FR-LS-04).
 *
 * @param listingsPostedInLastDay listings created in the rolling 24-hour window
 * (DEC-LS-7)
 * @param hasCompletedTransaction lifts the daily limit; always false until the
 * transaction module exists (register LS-W1)
 */
public record PostingHistory(int listingsPostedInLastDay, int openListings, boolean hasCompletedTransaction) {

}
