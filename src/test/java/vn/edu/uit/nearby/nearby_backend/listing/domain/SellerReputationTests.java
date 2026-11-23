package vn.edu.uit.nearby.nearby_backend.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vn.edu.uit.nearby.nearby_backend.listing.ListingSnapshot;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

class SellerReputationTests {

	@ParameterizedTest
	@ValueSource(strings = { "0", "50", "100" })
	void acceptsScoresFromZeroToOneHundred(String score) {
		assertThat(SellerReputation.of(new BigDecimal(score), "NEW").score()).isEqualByComparingTo(score);
	}

	@ParameterizedTest
	@ValueSource(strings = { "-0.01", "100.01" })
	void refusesScoresOutsideTheRange(String score) {
		assertThatThrownBy(() -> SellerReputation.of(new BigDecimal(score), "NEW"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("FR-RP-08 / OI-05: the five tiers are accepted, others are not")
	void acceptsOnlyTheFiveTiers() {
		for (SellerTier tier : SellerTier.values()) {
			assertThat(SellerReputation.of(BigDecimal.ONE, tier.name()).tier()).isEqualTo(tier);
		}
		assertThatThrownBy(() -> SellerReputation.of(BigDecimal.ONE, "GOLD"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("FR-DC-03: a new listing copies the seller's reputation")
	void aListingCopiesTheReputation() {
		Listing listing = ListingFixtures.anActiveListing();

		listing.copySellerReputation(SellerReputation.of(new BigDecimal("61.25"), "STANDARD"));

		assertThat(listing.getSellerScore()).isEqualByComparingTo("61.25");
		assertThat(listing.getSellerTier()).isEqualTo(SellerTier.STANDARD);
	}

	@Test
	@DisplayName("FR-CH-01 / FR-TX-01: RESERVED takes chats but not offers; free means price 0")
	void snapshotTellsWhatTheListingTakes() {
		ListingSnapshot reserved = new ListingSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Ghế",
				"RESERVED", 0, true, new GeoPoint(10, 106), UUID.randomUUID());

		assertThat(reserved.takesNewChats()).isTrue();
		assertThat(reserved.takesNewOffers()).isFalse();
		assertThat(reserved.isFree()).isTrue();
	}

}
