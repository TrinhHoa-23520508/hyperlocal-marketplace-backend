package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The seller's score R (0-100) and tier as copied onto listings ({@code seller_score},
 * {@code seller_tier}); pushed by the reputation module (DEC-LS-2).
 */
public record SellerReputation(BigDecimal score, SellerTier tier) {

	private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

	public SellerReputation {
		Objects.requireNonNull(tier);
		if (score.signum() < 0 || score.compareTo(MAX_SCORE) > 0) {
			throw new IllegalArgumentException("A seller score is 0-100: " + score);
		}
	}

	/**
	 * @throws IllegalArgumentException for a tier name that FR-RP-08 does not define
	 */
	public static SellerReputation of(BigDecimal score, String tier) {
		return new SellerReputation(score, SellerTier.valueOf(tier));
	}

}
