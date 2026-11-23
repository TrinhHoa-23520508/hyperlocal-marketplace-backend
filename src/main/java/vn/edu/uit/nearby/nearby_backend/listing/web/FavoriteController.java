package vn.edu.uit.nearby.nearby_backend.listing.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import vn.edu.uit.nearby.nearby_backend.listing.application.FavoriteService;
import vn.edu.uit.nearby.nearby_backend.listing.web.dto.FavoriteResponse;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;

@Tag(name = "Favorites", description = "Saved listings (FR-LS-12; A10, A15)")
@RestController
public class FavoriteController {

	private final FavoriteService favorites;

	public FavoriteController(FavoriteService favorites) {
		this.favorites = favorites;
	}

	@Operation(summary = "Save a listing; saving again changes nothing (UC-LS-08)")
	@PutMapping(ApiPaths.LISTING_DETAIL + "/favorite")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "404", description = "LISTING_NOT_FOUND: the listing cannot be seen")
	public void save(@PathVariable UUID listingId) {
		favorites.save(CurrentUser.requireId(), listingId);
	}

	@Operation(summary = "Unsave a listing; works on listings that are no longer available (UC-LS-08)")
	@DeleteMapping(ApiPaths.LISTING_DETAIL + "/favorite")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unsave(@PathVariable UUID listingId) {
		favorites.unsave(CurrentUser.requireId(), listingId);
	}

	@Operation(summary = "The caller's saved listings, newest first (UC-LS-09, A15)")
	@GetMapping(ApiPaths.CURRENT_USER_FAVORITES)
	@ApiResponse(responseCode = "400", description = "INVALID_CURSOR")
	public CursorPage<FavoriteResponse> savedListings(@RequestParam(required = false) @Nullable String cursor,
			@RequestParam(required = false) @Nullable Integer limit) {
		return favorites.savedListings(CurrentUser.requireId(), cursor, limit).map(FavoriteResponse::from);
	}

}
