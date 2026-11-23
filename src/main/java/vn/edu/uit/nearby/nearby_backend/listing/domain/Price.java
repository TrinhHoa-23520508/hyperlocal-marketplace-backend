package vn.edu.uit.nearby.nearby_backend.listing.domain;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * Asking price in whole VND, 0-1 000 000 000; 0 means the item is given away (FR-LS-01,
 * BR-09; {@code listings.price}).
 */
public record Price(long amountVnd) {

	public static final long MAX_VND = 1_000_000_000L;

	public Price {
		if (amountVnd < 0 || amountVnd > MAX_VND) {
			throw InvalidRequestException.invalidField("price", "The price must be 0-" + MAX_VND + " VND.");
		}
	}

	public boolean isFree() {
		return amountVnd == 0;
	}

	public boolean isLowerThan(Price other) {
		return amountVnd < other.amountVnd;
	}

}
