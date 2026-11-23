package vn.edu.uit.nearby.nearby_backend.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.listing.domain.BumpRules;
import vn.edu.uit.nearby.nearby_backend.listing.domain.PostingQuota;

/**
 * Values are the ones of SRS table {@code tab:params}, as seeded by V9 and V14.
 */
class ListingParametersTests {

	static final Map<String, String> SEEDED = Map.ofEntries(Map.entry("listing.blur_m", "300"),
			Map.entry("listing.max_images", "10"), Map.entry("listing.max_open_per_user", "30"),
			Map.entry("listing.new_account_daily_limit", "3"), Map.entry("listing.bump_cooldown_hours", "48"),
			Map.entry("listing.bump_max_per_listing", "10"), Map.entry("image.max_bytes", "5242880"),
			Map.entry("upload.orphan_ttl_hours", "24"), Map.entry("search.max_radius_km", "20"),
			Map.entry("search.page_size", "20"), Map.entry("search.rank_weights", "0.5,0.3,0.2"));

	private final ListingParameters parameters = new ListingParameters(SEEDED::get);

	@Test
	void blursOnAThreeHundredMeterGrid() {
		assertThat(parameters.blurGrid().cellSizeMeters()).isEqualTo(300);
	}

	@Test
	void allowsTenImagesPerListing() {
		assertThat(parameters.maxImages()).isEqualTo(10);
	}

	@Test
	void limitsPostingToThreeADayAndThirtyOpenListings() {
		assertThat(parameters.postingQuota()).isEqualTo(new PostingQuota(3, 30));
		assertThat(parameters.dailyPostingWindow()).isEqualTo(Duration.ofHours(24));
	}

	@Test
	void allowsABumpEveryFortyEightHoursUpToTenTimes() {
		assertThat(parameters.bumpRules()).isEqualTo(new BumpRules(Duration.ofHours(48), 10));
	}

	@Test
	void acceptsImagesUpToFiveMegabytes() {
		assertThat(parameters.imageUploadPolicy().maxSizeBytes()).isEqualTo(5_242_880);
	}

	@Test
	void deletesOrphanImagesAfterTwentyFourHours() {
		assertThat(parameters.orphanImageTtl()).isEqualTo(Duration.ofHours(24));
	}

	@Test
	void searchesUpToTwentyKilometersTwentyListingsAPage() {
		assertThat(parameters.maxSearchRadiusKm()).isEqualTo(20);
		assertThat(parameters.pageSize()).isEqualTo(20);
	}

	@Test
	void ranksWithTheSeededWeights() {
		assertThat(parameters.relevanceScore().score(0, 1_000, null, Duration.ZERO)).isCloseTo(0.5 + 0.15 + 0.2,
				within(1e-9));
	}

}
