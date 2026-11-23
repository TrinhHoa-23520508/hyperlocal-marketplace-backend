package vn.edu.uit.nearby.nearby_backend.listing.web;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.CategoryResponse;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.FeedParams;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.ListingCardResponse;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.ListingPageResponse;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.MapParams;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.MapResponse;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.SearchParams;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;

/**
 * Public, read-only discovery (FR-DC-01..07, FR-DC-12). A token, when sent, identifies
 * the viewer for the default center and the block filter.
 */
@Tag(name = "Discovery", description = "Nearby feed, search, map and categories (FR-DC-01..07; A06-A09, A11)")
@RestController
@SecurityRequirements
public class DiscoveryController {

	private final DiscoveryService discovery;

	public DiscoveryController(DiscoveryService discovery) {
		this.discovery = discovery;
	}

	@Operation(summary = "Nearby feed: ACTIVE and RESERVED listings, newest or bumped first, 20 a page (UC-DC-01, A06)",
			description = "Centered on lat/lon (GPS or picked point) with rangeLevel, or on the caller's active "
					+ "area. Guests must send lat and lon.")
	@GetMapping(ApiPaths.FEED)
	@ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: no center (field center) or bad values | "
			+ "INVALID_RANGE: rangeLevel | INVALID_CURSOR: reload from the first page")
	public ListingPageResponse feed(@Valid @ParameterObject FeedParams params) {
		return ListingPageResponse.from(discovery.feed(params.origin(viewer()), params.cursor()));
	}

	@Operation(summary = "Search with keyword, filters and sort, 20 a page (UC-DC-02, A07/A08)")
	@GetMapping(ApiPaths.SEARCH)
	@ApiResponse(responseCode = "400",
			description = "VALIDATION_FAILED: minPrice above maxPrice, unknown category or sort, no center | "
					+ "INVALID_RANGE | INVALID_CURSOR")
	public ListingPageResponse search(@Valid @ParameterObject SearchParams params) {
		return ListingPageResponse.from(discovery.search(params.toQuery(viewer()), params.cursor()));
	}

	@Operation(summary = "Map markers for a viewport: up to 200 points, otherwise clusters (UC-DC-03, A09)")
	@GetMapping(ApiPaths.MAP_LISTINGS)
	@ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: viewport or zoom")
	public MapResponse map(@Valid @ParameterObject MapParams params) {
		return MapResponse.from(discovery.map(params.toQuery(viewer())));
	}

	@Operation(summary = "Active categories in display order (A08, A12)")
	@GetMapping(ApiPaths.CATEGORIES)
	public List<CategoryResponse> categories() {
		return discovery.activeCategories().stream().map(CategoryResponse::from).toList();
	}

	@Operation(summary = "A seller's ACTIVE and RESERVED listings for the public profile (FR-ID-13, A11)")
	@GetMapping(ApiPaths.PUBLIC_USER_LISTINGS)
	@ApiResponse(responseCode = "400", description = "INVALID_CURSOR")
	public CursorPage<ListingCardResponse> sellerListings(@PathVariable UUID userId,
			@RequestParam(required = false) @Nullable String cursor,
			@RequestParam(required = false) @Nullable Integer limit) {
		return discovery.sellerListings(userId, viewer(), cursor, limit).map(ListingCardResponse::from);
	}

	private static @Nullable UUID viewer() {
		return CurrentUser.id().orElse(null);
	}

}
