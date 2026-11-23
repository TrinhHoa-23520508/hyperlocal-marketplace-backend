package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;

@Schema(description = "A new listing (UC-LS-01, A12)")
public record CreateListingRequest(
		@Schema(description = "One of the caller's VERIFIED or EXPIRING areas; it never changes") @NotNull UUID areaId,
		@Schema(example = "Áo khoác da nam", description = "5-100 characters") @NotBlank @Size(max = 100) String title,
		@Schema(description = "At most 2 000 characters; line breaks kept") @Size(
				max = 2000) @Nullable String description,
		@NotNull ItemCondition condition,
		@Schema(example = "150000",
				description = "Whole VND, 0-1 000 000 000; 0 = given away") @NotNull @PositiveOrZero @Max(1_000_000_000) Long priceVnd,
		@Schema(description = "false: offers must match the price (FR-TX-01); default true") @Nullable Boolean acceptOffers,
		@Schema(example = "Cổng KTX khu A",
				description = "At most 100 characters") @Size(max = 100) @Nullable String meetingSpot,
		@Schema(example = "1") @NotNull Short categoryId,
		@Schema(description = "Exact pin, private; within 1 500 m of the area center") @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
		@Schema(description = "1-10 READY image ids in display order; the first is the cover") @NotNull @Size(min = 1,
				max = 10) List<@NotNull UUID> imageIds)
		implements
			ListingFormFields {

}
