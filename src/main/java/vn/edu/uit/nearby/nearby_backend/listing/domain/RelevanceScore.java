package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.math.BigDecimal;
import java.time.Duration;

import org.jspecify.annotations.Nullable;

/**
 * The "relevant" sort of SRS appendix D (FR-DC-03): score = w1 (1 - d/D) + w2 R/100 + w3
 * exp(-age / 7 days), where d is the distance to the public location, D the search
 * radius, R the seller score (50 without reviews) and age is measured from
 * {@code feed_at}. Ties are broken by {@code feed_at}, then {@code id}.
 */
public record RelevanceScore(RankWeights weights) {

	/**
	 * Initial value; the SRS marks it for calibration.
	 */
	public static final Duration FRESHNESS_SCALE = Duration.ofDays(7);

	public static final double BASELINE_SELLER_SCORE = 50;

	private static final double MAX_SELLER_SCORE = 100;

	public double score(double distanceMeters, double radiusMeters, @Nullable BigDecimal sellerScore, Duration age) {
		double proximity = 1 - Math.clamp(distanceMeters / radiusMeters, 0, 1);
		double reputation = (sellerScore == null ? BASELINE_SELLER_SCORE : sellerScore.doubleValue())
				/ MAX_SELLER_SCORE;
		double freshness = Math.exp(-(double) Math.max(0, age.toMillis()) / FRESHNESS_SCALE.toMillis());
		return weights.distance() * proximity + weights.reputation() * reputation + weights.freshness() * freshness;
	}

}
