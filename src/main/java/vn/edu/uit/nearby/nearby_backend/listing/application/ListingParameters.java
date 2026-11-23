package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Duration;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.listing.domain.BumpRules;
import vn.edu.uit.nearby.nearby_backend.listing.domain.PostingQuota;
import vn.edu.uit.nearby.nearby_backend.listing.domain.RankWeights;
import vn.edu.uit.nearby.nearby_backend.listing.domain.RelevanceScore;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridSnapper;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageUploadPolicy;

/**
 * Typed access to the listing and discovery runtime parameters (SRS table
 * {@code tab:params}); values are seeded by Flyway V9 and V14. Read on every call so a
 * changed value applies without a restart (FR-DC-03 "không triển khai lại").
 */
@Component
public class ListingParameters {

	private static final String BLUR_METERS = "listing.blur_m";

	private static final String MAX_IMAGES = "listing.max_images";

	private static final String MAX_OPEN_PER_USER = "listing.max_open_per_user";

	private static final String NEW_ACCOUNT_DAILY_LIMIT = "listing.new_account_daily_limit";

	private static final String BUMP_COOLDOWN_HOURS = "listing.bump_cooldown_hours";

	private static final String BUMP_MAX_PER_LISTING = "listing.bump_max_per_listing";

	private static final String IMAGE_MAX_BYTES = "image.max_bytes";

	private static final String ORPHAN_TTL_HOURS = "upload.orphan_ttl_hours";

	private static final String MAX_RADIUS_KM = "search.max_radius_km";

	private static final String PAGE_SIZE = "search.page_size";

	private static final String RANK_WEIGHTS = "search.rank_weights";

	private final AppParameters parameters;

	public ListingParameters(AppParameters parameters) {
		this.parameters = parameters;
	}

	public GridSnapper blurGrid() {
		return new GridSnapper(parameters.getInt(BLUR_METERS));
	}

	public int maxImages() {
		return parameters.getInt(MAX_IMAGES);
	}

	public PostingQuota postingQuota() {
		return new PostingQuota(parameters.getInt(NEW_ACCOUNT_DAILY_LIMIT), parameters.getInt(MAX_OPEN_PER_USER));
	}

	/**
	 * The daily limit counts in a rolling 24-hour window (DEC-LS-7).
	 */
	public Duration dailyPostingWindow() {
		return Duration.ofDays(1);
	}

	public BumpRules bumpRules() {
		return new BumpRules(Duration.ofHours(parameters.getInt(BUMP_COOLDOWN_HOURS)),
				parameters.getInt(BUMP_MAX_PER_LISTING));
	}

	/**
	 * {@code image.max_bytes} holds the same limit as {@code upload.max_image_mb} in
	 * bytes (SRS {@code tab:params}).
	 */
	public ImageUploadPolicy imageUploadPolicy() {
		return new ImageUploadPolicy(parameters.getInt(IMAGE_MAX_BYTES));
	}

	public Duration orphanImageTtl() {
		return Duration.ofHours(parameters.getInt(ORPHAN_TTL_HOURS));
	}

	public int maxSearchRadiusKm() {
		return parameters.getInt(MAX_RADIUS_KM);
	}

	public int pageSize() {
		return parameters.getInt(PAGE_SIZE);
	}

	public RelevanceScore relevanceScore() {
		return new RelevanceScore(RankWeights.parse(parameters.get(RANK_WEIGHTS)));
	}

}
