package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.util.Arrays;

/**
 * Weights of distance, seller reputation and freshness in the "relevant" sort
 * ({@code search.rank_weights} = 0.5,0.3,0.2; FR-DC-03).
 */
public record RankWeights(double distance, double reputation, double freshness) {

	private static final int WEIGHT_COUNT = 3;

	public RankWeights {
		if (distance < 0 || reputation < 0 || freshness < 0) {
			throw new IllegalArgumentException("Rank weights cannot be negative.");
		}
	}

	/**
	 * @param commaSeparated the parameter value, for example {@code 0.5,0.3,0.2}
	 */
	public static RankWeights parse(String commaSeparated) {
		double[] weights = Arrays.stream(commaSeparated.split(","))
			.map(String::strip)
			.mapToDouble(Double::parseDouble)
			.toArray();
		if (weights.length != WEIGHT_COUNT) {
			throw new IllegalArgumentException("Expected " + WEIGHT_COUNT + " rank weights: " + commaSeparated);
		}
		return new RankWeights(weights[0], weights[1], weights[2]);
	}

}
