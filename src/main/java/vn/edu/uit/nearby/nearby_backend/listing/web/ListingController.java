package vn.edu.uit.nearby.nearby_backend.listing.web;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.ChangeListing;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.CreateListing;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.EditListing;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingDetailService;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingDetailService.Viewer;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingLifecycleService;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingPublishingService;
import vn.edu.uit.nearby.nearby_backend.listing.application.MyListingsService;
import vn.edu.uit.nearby.nearby_backend.listing.application.OwnerListingView;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.CreateListingRequest;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.EditListingRequest;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.ListingDetailResponse;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.MyListingsResponse;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.OwnerListingResponse;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.PostingEligibilityResponse;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.VersionRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.constant.HeaderNames;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;
import vn.edu.uit.nearby.nearby_backend.shared.web.idempotency.Idempotent;

@Tag(name = "Listings",
		description = "Post and manage listings, listing detail (FR-LS-01..14, FR-DC-08; A10, A12, A14)")
@RestController
@RequestMapping(ApiPaths.LISTINGS_BASE)
public class ListingController {

	private static final String IDEMPOTENCY_KEY_DESCRIPTION = "UUID; resending the same key and body returns the "
			+ "stored response";

	private final ListingPublishingService publishing;

	private final ListingLifecycleService lifecycle;

	private final MyListingsService myListings;

	private final ListingDetailService details;

	public ListingController(ListingPublishingService publishing, ListingLifecycleService lifecycle,
			MyListingsService myListings, ListingDetailService details) {
		this.publishing = publishing;
		this.lifecycle = lifecycle;
		this.myListings = myListings;
		this.details = details;
	}

	@Operation(summary = "Whether the caller may post now: trading gate and remaining quota (UC-LS-01 step 1)")
	@GetMapping("/posting-eligibility")
	public PostingEligibilityResponse postingEligibility() {
		return PostingEligibilityResponse.from(publishing.postingEligibility(CurrentUser.requireId()));
	}

	@Operation(summary = "Post a listing; it is ACTIVE at once (UC-LS-01, A12)")
	@Idempotent
	@Parameter(name = HeaderNames.IDEMPOTENCY_KEY, in = ParameterIn.HEADER, required = true,
			description = IDEMPOTENCY_KEY_DESCRIPTION)
	@PostMapping
	@ApiResponse(responseCode = "201", description = "The new listing; Location points to its detail")
	@ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_REQUIRED")
	@ApiResponse(responseCode = "403", description = "TRADING_GATE_FAILED: reasons lists the conditions to fix")
	@ApiResponse(responseCode = "409",
			description = "LISTING_OPEN_LIMIT: 30 open listings already | IDEMPOTENCY_CONFLICT | DUPLICATE_REQUEST")
	@ApiResponse(responseCode = "422",
			description = "PIN_OUTSIDE_AREA: the area is not a valid area of the caller or the pin is more than "
					+ "1 500 m from its center | IMAGE_INVALID: an image is not the caller's, not READY or used by "
					+ "another listing | CATEGORY_INACTIVE")
	@ApiResponse(responseCode = "429",
			description = "LISTING_DAILY_LIMIT: 3 listings a day until the first completed transaction; "
					+ "remaining, dailyLimit and Retry-After")
	public ResponseEntity<OwnerListingResponse> create(@Valid @RequestBody CreateListingRequest request) {
		OwnerListingView created = publishing
			.create(new CreateListing(CurrentUser.requireId(), request.areaId(), request.toForm()));
		return ResponseEntity.created(URI.create(ApiPaths.LISTINGS_BASE + "/" + created.id()))
			.body(OwnerListingResponse.from(created));
	}

	@Operation(summary = "Edit every field but the area (UC-LS-02, A12)")
	@PutMapping("/{listingId}")
	@ApiResponse(responseCode = "404", description = "LISTING_NOT_FOUND: not one of the caller's listings")
	@ApiResponse(responseCode = "409",
			description = "CONCURRENT_MODIFICATION: reload; currentVersion | INVALID_LISTING_STATE: SOLD, REMOVED "
					+ "or DELETED | LISTING_HAS_OPEN_TRANSACTIONS: price change while RESERVED; transactionId")
	@ApiResponse(responseCode = "422", description = "PIN_OUTSIDE_AREA | IMAGE_INVALID | CATEGORY_INACTIVE")
	public OwnerListingResponse edit(@PathVariable UUID listingId, @Valid @RequestBody EditListingRequest request) {
		return OwnerListingResponse.from(publishing
			.edit(new EditListing(CurrentUser.requireId(), listingId, request.version(), request.toForm())));
	}

	@Operation(summary = "Hide: off the feed, search and map, still in \"Tin của tôi\" (UC-LS-03, A14)")
	@PostMapping("/{listingId}/hide")
	@ApiResponse(responseCode = "404", description = "LISTING_NOT_FOUND")
	@ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION | INVALID_LISTING_STATE: not ACTIVE | "
			+ "LISTING_HAS_OPEN_TRANSACTIONS: RESERVED; transactionId")
	public OwnerListingResponse hide(@PathVariable UUID listingId, @Valid @RequestBody VersionRequest request) {
		return OwnerListingResponse.from(lifecycle.hide(change(listingId, request)));
	}

	@Operation(summary = "Show a listing the caller hid (UC-LS-03, A14)")
	@PostMapping("/{listingId}/unhide")
	@ApiResponse(responseCode = "404", description = "LISTING_NOT_FOUND")
	@ApiResponse(responseCode = "409",
			description = "CONCURRENT_MODIFICATION | INVALID_LISTING_STATE: not HIDDEN, or hidden because its area "
					+ "was removed or the account was sanctioned (hiddenReason)")
	public OwnerListingResponse unhide(@PathVariable UUID listingId, @Valid @RequestBody VersionRequest request) {
		return OwnerListingResponse.from(lifecycle.unhide(change(listingId, request)));
	}

	@Operation(summary = "Mark sold outside the app: no transaction, no review (UC-LS-05 A1, A14)")
	@Idempotent
	@Parameter(name = HeaderNames.IDEMPOTENCY_KEY, in = ParameterIn.HEADER, required = true,
			description = IDEMPOTENCY_KEY_DESCRIPTION)
	@PostMapping("/{listingId}/sold-outside")
	@ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_REQUIRED")
	@ApiResponse(responseCode = "404", description = "LISTING_NOT_FOUND")
	@ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION | INVALID_LISTING_STATE: not ACTIVE | "
			+ "IDEMPOTENCY_CONFLICT | DUPLICATE_REQUEST")
	public OwnerListingResponse markSoldOutside(@PathVariable UUID listingId,
			@Valid @RequestBody VersionRequest request) {
		return OwnerListingResponse.from(lifecycle.markSoldOutsideApp(change(listingId, request)));
	}

	@Operation(summary = "Bump to the top of the feed: once per 48 h, 10 times at most (UC-LS-06, A14)")
	@PostMapping("/{listingId}/bump")
	@ApiResponse(responseCode = "404", description = "LISTING_NOT_FOUND")
	@ApiResponse(responseCode = "409",
			description = "BUMP_LIMIT_REACHED: 10 bumps done | INVALID_LISTING_STATE: not ACTIVE")
	@ApiResponse(responseCode = "429", description = "BUMP_TOO_SOON: nextAllowedAt and Retry-After")
	public OwnerListingResponse bump(@PathVariable UUID listingId) {
		return OwnerListingResponse.from(lifecycle.bump(CurrentUser.requireId(), listingId));
	}

	@Operation(summary = "Delete: 404 for everyone afterwards; conversations stay readable (UC-LS-04, A14)")
	@DeleteMapping("/{listingId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "404", description = "LISTING_NOT_FOUND")
	@ApiResponse(responseCode = "409", description = "LISTING_HAS_OPEN_TRANSACTIONS: an OFFERED or ACCEPTED "
			+ "transaction; transactionId | INVALID_LISTING_STATE: SOLD or REMOVED")
	public void delete(@PathVariable UUID listingId) {
		lifecycle.delete(CurrentUser.requireId(), listingId);
	}

	@Operation(summary = "The caller's listings by state, with counters (UC-LS-07, A14)")
	@GetMapping("/me")
	@ApiResponse(responseCode = "400", description = "INVALID_CURSOR | VALIDATION_FAILED: unknown status")
	public MyListingsResponse myListings(
			@Parameter(description = "Tab filter, repeatable; default every state but DELETED") @RequestParam(
					required = false) @Nullable List<ListingStatus> status,
			@RequestParam(required = false) @Nullable String cursor,
			@RequestParam(required = false) @Nullable Integer limit) {
		Set<ListingStatus> statuses = status == null ? Set.of() : Set.copyOf(status);
		return MyListingsResponse.from(myListings.myListings(CurrentUser.requireId(), statuses, cursor, limit));
	}

	@Operation(summary = "Listing detail with buttons and their reasons (UC-DC-04, A10); counts a view",
			description = "Public. The owner also gets the exact pin. lat and lon, when given, are where the "
					+ "distance is measured from; otherwise the caller's active area.")
	@SecurityRequirements
	@GetMapping("/{listingId}")
	@ApiResponse(responseCode = "404",
			description = "LISTING_NOT_FOUND: deleted, removed, hidden, or between users who blocked each other")
	public ListingDetailResponse detail(@PathVariable UUID listingId,
			@RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") @Nullable Double lat,
			@RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") @Nullable Double lon,
			HttpServletRequest http) {
		GeoPoint origin = lat == null || lon == null ? null : new GeoPoint(lat, lon);
		Viewer viewer = new Viewer(CurrentUser.id().orElse(null), GuestFingerprint.of(http));
		return ListingDetailResponse.from(details.detail(listingId, viewer, origin));
	}

	private static ChangeListing change(UUID listingId, VersionRequest request) {
		return new ChangeListing(CurrentUser.requireId(), listingId, request.version());
	}

}
