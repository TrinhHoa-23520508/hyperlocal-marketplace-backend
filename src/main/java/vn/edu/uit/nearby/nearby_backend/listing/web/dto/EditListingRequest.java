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

@Schema(description = "Every field but the area (UC-LS-02, A12). Send the whole form back")
public record EditListingRequest(@Schema(
		description = "The version the seller edited; a newer one is 409 CONCURRENT_MODIFICATION") @NotNull Long version,
		@NotBlank @Size(max = 100) String title, @Size(max = 2000) @Nullable String description,
		@NotNull ItemCondition condition, @NotNull @PositiveOrZero @Max(1_000_000_000) Long priceVnd,
		@Nullable Boolean acceptOffers, @Size(max = 100) @Nullable String meetingSpot, @NotNull Short categoryId,
		@Schema(description = "A moved pin must stay inside the listing's area") @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
		@NotNull @Size(min = 1, max = 10) List<@NotNull UUID> imageIds) implements ListingFormFields {

}
