package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.SearchQuery;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SortOrder;

/**
 * UC-DC-02 inputs from A07/A08.
 */
public record SearchParams(
		@Parameter(description = "Keyword; accents and case are ignored (\"ao khoac\" finds \"Áo khoác\")") @Size(
				max = 100) @Nullable String q,
		@Nullable Short categoryId, @PositiveOrZero @Nullable Long minPrice, @PositiveOrZero @Nullable Long maxPrice,
		@Parameter(
				description = "Repeat for several: condition=NEW&condition=LIKE_NEW") @Nullable List<ItemCondition> condition,
		@Parameter(description = "Only free items (price 0)") @Nullable Boolean freeOnly,
		@Parameter(
				description = "1-20; outside values are clamped and flagged; default: the range of the center") @Positive @Nullable Double radiusKm,
		@Parameter(description = "Also SOLD listings") @Nullable Boolean includeSold,
		@Parameter(description = "NEWEST (default), NEAREST or RELEVANT") @Nullable SortOrder sort,
		@DecimalMin("-90") @DecimalMax("90") @Nullable Double lat,
		@DecimalMin("-180") @DecimalMax("180") @Nullable Double lon, @Nullable String rangeLevel,
		@Parameter(description = "nextCursor of the previous page of the same search") @Nullable String cursor)
		implements
			OriginParams {

	public SearchQuery toQuery(@Nullable UUID viewerId) {
		return new SearchQuery(origin(viewerId), q, categoryId, minPrice, maxPrice,
				condition == null ? Set.of() : Set.copyOf(condition), Boolean.TRUE.equals(freeOnly), radiusKm,
				Boolean.TRUE.equals(includeSold), sort == null ? SortOrder.NEWEST : sort);
	}

}
