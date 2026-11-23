package vn.edu.uit.nearby.nearby_backend.listing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * V14 against the SRS data dictionary (module listing) and table {@code tab:params}.
 */
@IntegrationTest
class ListingSchemaIntegrationTests {

	private static final String PIN = "POINT(106.8031 10.87)";

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	AppParameters appParameters;

	@Test
	@DisplayName("tab:params: seeds the listing, upload and search parameters")
	void seedsTheListingParametersOfTheSrs() {
		Map<String, String> expected = Map.ofEntries(Map.entry("listing.blur_m", "300"),
				Map.entry("listing.pin_radius_m", "1500"), Map.entry("listing.max_images", "10"),
				Map.entry("listing.max_open_per_user", "30"), Map.entry("listing.new_account_daily_limit", "3"),
				Map.entry("listing.bump_cooldown_hours", "48"), Map.entry("listing.bump_max_per_listing", "10"),
				Map.entry("upload.max_image_mb", "5"), Map.entry("image.max_bytes", "5242880"),
				Map.entry("upload.orphan_ttl_hours", "24"), Map.entry("search.max_radius_km", "20"),
				Map.entry("search.page_size", "20"), Map.entry("search.rank_weights", "0.5,0.3,0.2"));

		expected.forEach((key, value) -> assertThat(appParameters.get(key)).as(key).isEqualTo(value));
	}

	@Test
	void replacesTheOutdatedBlurParameter() {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM app_parameters WHERE key = 'location.blur_meters'",
				Integer.class);

		assertThat(count).isZero();
	}

	@Test
	void seedsTheCategoryCodesOfTheDataDictionary() {
		List<String> codes = jdbc.queryForList("SELECT code FROM categories WHERE active ORDER BY sort_order",
				String.class);

		assertThat(codes).containsExactly("ELECTRONICS", "HOUSEHOLD", "BOOKS", "FASHION", "VEHICLES", "BABY", "SPORTS",
				"FREE_SHARING", "OTHER");
	}

	@Test
	void storesAnActiveListing() {
		UUID id = insertListing("Áo khoác da", 150_000, "ACTIVE", null, null, null);

		assertThat(jdbc.queryForObject("SELECT is_free FROM listings WHERE id = ?", Boolean.class, id)).isFalse();
	}

	@Test
	@DisplayName("FR-LS-01/BR-09: price 0 is a free item")
	void marksAZeroPriceListingAsFree() {
		UUID id = insertListing("Tặng sách cũ", 0, "ACTIVE", null, null, null);

		assertThat(jdbc.queryForObject("SELECT is_free FROM listings WHERE id = ?", Boolean.class, id)).isTrue();
	}

	@Test
	@DisplayName("FR-LS-01: title must be 5-100 characters")
	void acceptsATitleOfFiveToOneHundredCharacters() {
		assertThatCode(() -> insertListing("Ghế đ", 1, "ACTIVE", null, null, null)).doesNotThrowAnyException();
		assertThatCode(() -> insertListing("Đ".repeat(100), 1, "ACTIVE", null, null, null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("FR-LS-01: title must be 5-100 characters")
	void refusesATitleOutsideFiveToOneHundredCharacters() {
		assertThatThrownBy(() -> insertListing("Ghế ", 1, "ACTIVE", null, null, null))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertListing("Đ".repeat(101), 1, "ACTIVE", null, null, null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("FR-LS-01: price above 1 000 000 000 is refused")
	void refusesAPriceAboveOneBillion() {
		assertThatCode(() -> insertListing("Xe máy cũ", 1_000_000_000, "ACTIVE", null, null, null))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> insertListing("Xe máy cũ", 1_000_000_001, "ACTIVE", null, null, null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void refusesAHiddenListingWithoutAReason() {
		assertThatThrownBy(() -> insertListing("Bàn học gỗ", 1, "HIDDEN", null, null, null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void refusesAHiddenReasonOnAVisibleListing() {
		assertThatThrownBy(() -> insertListing("Bàn học gỗ", 1, "ACTIVE", "OWNER", null, null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void refusesAReservedListingWithoutItsTransaction() {
		assertThatThrownBy(() -> insertListing("Bàn học gỗ", 1, "RESERVED", null, null, null))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatCode(() -> insertListing("Bàn học gỗ", 1, "RESERVED", null, UUID.randomUUID(), null))
			.doesNotThrowAnyException();
	}

	@Test
	void refusesARemovedListingWithoutAReason() {
		assertThatThrownBy(() -> insertListing("Bàn học gỗ", 1, "REMOVED", null, null, null))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatCode(() -> insertListing("Bàn học gỗ", 1, "REMOVED", null, null, "Hàng cấm"))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("FR-LS-10: feed_at = greatest(created_at, bumped_at)")
	void feedTimeFollowsTheLatestBump() {
		UUID id = insertListing("Nồi cơm điện", 200_000, "ACTIVE", null, null, null);
		Instant bumpedAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);

		jdbc.update("UPDATE listings SET bumped_at = ?, bump_count = 1 WHERE id = ?", Timestamp.from(bumpedAt), id);

		assertThat(jdbc.queryForObject("SELECT feed_at FROM listings WHERE id = ?", Timestamp.class, id).toInstant())
			.isEqualTo(bumpedAt);
	}

	@ParameterizedTest
	@ValueSource(strings = { "ao khoac", "Áo Khoác", "AO KHOAC" })
	@DisplayName("FR-DC-02: \"ao khoac\" matches \"Áo khoác\"")
	void fullTextSearchIgnoresAccentsAndCase(String query) {
		UUID id = insertListing("Áo khoác da nam", 300_000, "ACTIVE", null, null, null);

		assertThat(matchesFullText(id, query)).isTrue();
	}

	@Test
	void fullTextSearchFoldsTheVietnameseD() {
		UUID id = insertListing("Đồng hồ đeo tay", 300_000, "ACTIVE", null, null, null);

		assertThat(matchesFullText(id, "dong ho")).isTrue();
	}

	@Test
	void fullTextSearchCoversTheDescription() {
		UUID id = insertListing("Bàn học gỗ", 300_000, "ACTIVE", null, null, null);
		jdbc.update("UPDATE listings SET description = 'Còn mới, kèm ghế xoay' WHERE id = ?", id);

		assertThat(matchesFullText(id, "ghe xoay")).isTrue();
	}

	@Test
	void storesAnUnattachedImageWithoutAPosition() {
		assertThatCode(() -> insertImage(null, null, "UPLOADING", null)).doesNotThrowAnyException();
	}

	@Test
	void refusesAnAttachedImageWithoutAPosition() {
		UUID listingId = insertListing("Bàn học gỗ", 1, "ACTIVE", null, null, null);

		assertThatThrownBy(() -> insertImage(listingId, null, "READY", "thumb"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 11 })
	@DisplayName("FR-LS-01: image positions 1-10")
	void refusesAnImagePositionOutsideOneToTen(int position) {
		UUID listingId = insertListing("Bàn học gỗ", 1, "ACTIVE", null, null, null);

		assertThatThrownBy(() -> insertImage(listingId, position, "READY", "thumb"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void refusesTwoImagesInTheSamePosition() {
		UUID listingId = insertListing("Bàn học gỗ", 1, "ACTIVE", null, null, null);
		insertImage(listingId, 1, "READY", "thumb");

		assertThatThrownBy(() -> insertImage(listingId, 1, "READY", "thumb"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void refusesAReadyImageWithoutDerivatives() {
		assertThatThrownBy(() -> insertImage(null, null, "READY", null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("FR-LS-02: image size at most 5 242 880 bytes")
	void refusesAnImageOverFiveMegabytes() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO listing_images (id, owner_id, original_key, processing_status, size_bytes)
				VALUES (?, ?, 'incoming/x', 'UPLOADING', 5242881)
				""", UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("FR-LS-12: one favourite row per user and listing")
	void keepsOneFavoritePerUserAndListing() {
		UUID listingId = insertListing("Bàn học gỗ", 1, "ACTIVE", null, null, null);
		UUID userId = UUID.randomUUID();
		String insert = "INSERT INTO favorites (user_id, listing_id) VALUES (?, ?) ON CONFLICT DO NOTHING";

		jdbc.update(insert, userId, listingId);
		jdbc.update(insert, userId, listingId);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM favorites WHERE listing_id = ?", Integer.class, listingId))
			.isOne();
	}

	private UUID insertListing(String title, long price, String status, String hiddenReason, UUID reservedTransactionId,
			String removalReason) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO listings (id, seller_id, category_id, area_id, title, condition, price, status,
				                      hidden_reason, reserved_transaction_id, removal_reason,
				                      exact_location, public_location)
				VALUES (?, ?, (SELECT id FROM categories WHERE code = 'OTHER'), ?, ?, 'USED', ?, ?, ?, ?, ?,
				        ST_GeogFromText(?), ST_GeogFromText(?))
				""", id, UUID.randomUUID(), UUID.randomUUID(), title, price, status, hiddenReason,
				reservedTransactionId, removalReason, PIN, PIN);
		return id;
	}

	private void insertImage(UUID listingId, Integer position, String status, String derivativeKey) {
		jdbc.update("""
				INSERT INTO listing_images (id, listing_id, owner_id, original_key, thumb_key, large_key, position,
				                            processing_status)
				VALUES (?, ?, ?, 'incoming/x', ?, ?, ?, ?)
				""", UUID.randomUUID(), listingId, UUID.randomUUID(), derivativeKey, derivativeKey, position, status);
	}

	private boolean matchesFullText(UUID id, String query) {
		return jdbc.queryForObject("""
				SELECT search_vector @@ plainto_tsquery('simple', f_unaccent(?)) FROM listings WHERE id = ?
				""", Boolean.class, query, id);
	}

}
