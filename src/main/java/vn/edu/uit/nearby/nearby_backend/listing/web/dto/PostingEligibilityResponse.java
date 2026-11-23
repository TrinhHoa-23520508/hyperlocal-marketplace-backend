package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.PostingEligibility;

@Schema(description = "UC-LS-01 step 1: whether the caller may post now and why not")
public record PostingEligibilityResponse(boolean canPost,
		@Schema(description = "Trading gate conditions to fix (FR-ID-20); empty when open") List<String> gateFailures,
		@Schema(description = "Posts left in the rolling 24 h window; null once the seller has a completed transaction") @Nullable Integer remainingToday,
		@Schema(example = "3") int dailyLimit, @Schema(description = "Open listings still allowed") int remainingOpen,
		@Schema(example = "30") int maxOpenListings) {

	public static PostingEligibilityResponse from(PostingEligibility eligibility) {
		return new PostingEligibilityResponse(eligibility.canPost(),
				eligibility.tradingGate().failures().stream().map(Enum::name).toList(),
				eligibility.allowance().remainingToday(), eligibility.allowance().dailyLimit(),
				eligibility.allowance().remainingOpen(), eligibility.allowance().maxOpenListings());
	}

}
