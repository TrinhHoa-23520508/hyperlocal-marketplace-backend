package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * An item for sale or given away (FR-LS-01). Every status change goes through
 * {@link ListingStatus}, the single transition point of FR-LS-13; {@code version} guards
 * concurrent changes. The exact pin is private to the owner (FR-LS-03).
 */
@Getter
@Entity
@Table(name = "listings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Listing {

	public static final int MAX_REMOVAL_REASON_LENGTH = 200;

	@Id
	private UUID id;

	@Column(name = "seller_id", nullable = false, updatable = false)
	private UUID sellerId;

	@Column(name = "category_id", nullable = false)
	private short categoryId;

	@Column(name = "area_id", nullable = false, updatable = false)
	private UUID areaId;

	@Column(nullable = false)
	private String title;

	private @Nullable String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ItemCondition condition;

	@Column(nullable = false)
	private long price;

	@Column(name = "accept_offers", nullable = false)
	private boolean acceptOffers;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ListingStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "hidden_reason")
	private @Nullable HiddenReason hiddenReason;

	@Getter(AccessLevel.NONE)
	@Column(name = "exact_location", nullable = false, columnDefinition = "geography(Point,4326)")
	private Point exactLocation;

	@Getter(AccessLevel.NONE)
	@Column(name = "public_location", nullable = false, columnDefinition = "geography(Point,4326)")
	private Point publicLocation;

	@Column(name = "locality_name")
	private @Nullable String localityName;

	@Column(name = "meeting_spot_text")
	private @Nullable String meetingSpotText;

	@Column(name = "bumped_at")
	private @Nullable Instant bumpedAt;

	@Column(name = "bump_count", nullable = false)
	private short bumpCount;

	@Column(name = "view_count", nullable = false, insertable = false, updatable = false)
	private int viewCount;

	@Column(name = "favorite_count", nullable = false, insertable = false, updatable = false)
	private int favoriteCount;

	@Column(name = "chat_count", nullable = false, insertable = false, updatable = false)
	private int chatCount;

	@Column(name = "reserved_transaction_id")
	private @Nullable UUID reservedTransactionId;

	@Column(name = "seller_score", precision = 5, scale = 2)
	private @Nullable BigDecimal sellerScore;

	@Enumerated(EnumType.STRING)
	@Column(name = "seller_tier")
	private @Nullable SellerTier sellerTier;

	@Column(name = "removal_reason")
	private @Nullable String removalReason;

	@Column(name = "removed_by")
	private @Nullable UUID removedBy;

	@Column(name = "published_at")
	private @Nullable Instant publishedAt;

	@Column(name = "sold_at")
	private @Nullable Instant soldAt;

	@Column(name = "deleted_at")
	private @Nullable Instant deletedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	private Listing(NewListing listing, Instant now) {
		this.id = UUID.randomUUID();
		this.sellerId = listing.sellerId();
		this.areaId = listing.areaId();
		this.localityName = listing.localityName();
		this.createdAt = truncated(now);
		applyContent(listing.categoryId(), listing.content(), listing.location());
	}

	/**
	 * A new ACTIVE listing (R1 publishes without screening, {@code tab:sm-listing} first
	 * row). The trading gate, pin and quota checks happen before.
	 */
	public static Listing publish(NewListing listing, Instant now) {
		Listing published = new Listing(listing, now);
		published.status = ListingStatus.ACTIVE;
		published.publishedAt = truncated(now);
		published.touch(now);
		return published;
	}

	public boolean isOwnedBy(UUID userId) {
		return sellerId.equals(userId);
	}

	public Price askingPrice() {
		return new Price(price);
	}

	public GeoPoint pin() {
		return GeoPoint.from(exactLocation);
	}

	public GeoPoint publicPoint() {
		return GeoPoint.from(publicLocation);
	}

	/**
	 * A new listing starts with the seller's current reputation copy (FR-DC-03,
	 * FR-DC-05).
	 */
	public void copySellerReputation(SellerReputation reputation) {
		sellerScore = reputation.score();
		sellerTier = reputation.tier();
	}

	/**
	 * @throws ConflictException {@code CONCURRENT_MODIFICATION} when the client edited an
	 * older version (FR-LS-06, FR-LS-13)
	 */
	public void requireVersion(long expectedVersion) {
		if (version != expectedVersion) {
			throw new ConflictException(ErrorCodes.CONCURRENT_MODIFICATION,
					"The listing was changed by another request; reload and retry.", Map.of("currentVersion", version));
		}
	}

	/**
	 * FR-LS-06: every field but the area; never a SOLD, REMOVED or DELETED listing; no
	 * price change while a transaction holds the listing.
	 */
	public void edit(ListingChanges changes, Instant now) {
		if (!status.isEditable()) {
			throw status.invalidMove("edited");
		}
		if (status == ListingStatus.RESERVED && !askingPrice().equals(changes.content().price())) {
			throw heldByTransaction("change the price of");
		}
		applyContent(changes.categoryId(), changes.content(), changes.location());
		touch(now);
	}

	/**
	 * FR-LS-07: ACTIVE to HIDDEN by the owner; refused while RESERVED, naming the holding
	 * transaction.
	 */
	public void hide(Instant now) {
		if (status == ListingStatus.RESERVED) {
			throw heldByTransaction("hide");
		}
		moveToHidden(status.afterOwnerHide(), HiddenReason.OWNER, now);
	}

	/**
	 * FR-LS-07: back to ACTIVE, only when the owner hid it. A listing hidden because its
	 * area was removed or its seller was sanctioned is not the owner's to show.
	 */
	public void unhide(Instant now) {
		requireHiddenFor(HiddenReason.OWNER, "shown again by its owner");
		moveToVisible(status.afterOwnerUnhide(), now);
	}

	/**
	 * FR-LS-08: soft delete. Open OFFERED/ACCEPTED transactions are checked by the
	 * caller; a RESERVED listing is refused here.
	 */
	public void delete(Instant now) {
		if (status == ListingStatus.RESERVED) {
			throw heldByTransaction("delete");
		}
		moveToVisible(status.afterOwnerDeletion(), now);
		markDeleted(now);
	}

	/**
	 * UC-SY-12: the seller's account was anonymised (SRS table {@code tab:retention}).
	 * The listing becomes DELETED unless it is already gone, and no listing keeps its
	 * pin.
	 */
	public void anonymiseForDeletedAccount(Instant now) {
		if (!status.isGone()) {
			moveToVisible(status.afterAccountAnonymised(), now);
			reservedTransactionId = null;
			markDeleted(now);
		}
		exactLocation = publicLocation;
		touch(now);
	}

	/**
	 * Owners see every state but DELETED; anyone else only public states (FR-ID-18:
	 * another user's private listing does not exist for them).
	 */
	public boolean isVisibleTo(@Nullable UUID viewerId) {
		if (status == ListingStatus.DELETED) {
			return false;
		}
		return status.isPubliclyVisible() || (viewerId != null && isOwnedBy(viewerId));
	}

	/**
	 * FR-LS-09: ACTIVE to SOLD, either outside the app or to a chat partner
	 * ({@code SaleMarked}).
	 */
	public void markSold(Instant now) {
		status = status.afterDirectSale();
		soldAt = truncated(now);
		touch(now);
	}

	/**
	 * A transaction was ACCEPTED (an accepted offer or a reservation for a chat partner).
	 */
	public void reserve(UUID transactionId, Instant now) {
		status = status.afterReservation();
		reservedTransactionId = transactionId;
		touch(now);
	}

	/**
	 * The holding transaction was CANCELLED or EXPIRED.
	 */
	public void releaseReservation(UUID transactionId, Instant now) {
		requireHeldBy(transactionId);
		status = status.afterReservationReleased();
		reservedTransactionId = null;
		touch(now);
	}

	/**
	 * The holding transaction was COMPLETED, or the seller marked the listing sold to its
	 * buyer.
	 */
	public void completeReservedSale(UUID transactionId, Instant now) {
		requireHeldBy(transactionId);
		status = status.afterReservedSale();
		reservedTransactionId = null;
		soldAt = truncated(now);
		touch(now);
	}

	/**
	 * The buyer denied a marked sale within the deny window ({@code SaleDenied}); the
	 * transaction module owns the window.
	 */
	public void revertDeniedSale(Instant now) {
		status = status.afterSaleDenied();
		soldAt = null;
		touch(now);
	}

	/**
	 * A moderator removes the listing with a reason (FR-MD-02).
	 */
	public void removeByModerator(String reason, UUID moderatorId, Instant now) {
		String stripped = Objects.requireNonNull(reason).strip();
		if (stripped.isEmpty() || stripped.length() > MAX_REMOVAL_REASON_LENGTH) {
			throw new IllegalArgumentException(
					"A removal reason of 1-" + MAX_REMOVAL_REASON_LENGTH + " characters is required.");
		}
		moveToVisible(status.afterModeratorRemoval(), now);
		reservedTransactionId = null;
		removalReason = stripped;
		removedBy = moderatorId;
	}

	/**
	 * FR-AR-07: the listing's area was removed ({@code AreaRemoved}). An owner- or
	 * sanction-hidden listing now stays hidden for this reason, since its area is gone.
	 */
	public void hideForRemovedArea(Instant now) {
		moveToHidden(status.afterAreaRemoved(), HiddenReason.AREA_REMOVED, now);
	}

	/**
	 * FR-MD-05: the seller was suspended or banned ({@code UserSanctioned}).
	 */
	public void hideForSanction(Instant now) {
		moveToHidden(status.afterSanction(), HiddenReason.SANCTION, now);
	}

	/**
	 * The sanction was lifted or the suspension ended: back to ACTIVE, only when the
	 * sanction is what hid it.
	 */
	public void restoreAfterSanction(Instant now) {
		requireHiddenFor(HiddenReason.SANCTION, "restored after a sanction");
		moveToVisible(status.afterSanctionLifted(), now);
	}

	/**
	 * FR-LS-10: moves an ACTIVE listing to the top of the feed ({@code feed_at} follows
	 * {@code bumped_at}).
	 * @throws RateLimitExceededException {@code BUMP_TOO_SOON} with {@code nextAllowedAt}
	 * within the cooldown since the last bump
	 * @throws ConflictException {@code BUMP_LIMIT_REACHED} after the last allowed bump
	 */
	public void bump(BumpRules rules, Instant now) {
		if (status != ListingStatus.ACTIVE) {
			throw status.invalidMove("bumped");
		}
		if (bumpCount >= rules.maxBumps()) {
			throw new ConflictException(ErrorCodes.BUMP_LIMIT_REACHED,
					"A listing can be bumped at most " + rules.maxBumps() + " times.",
					Map.of("maxBumps", rules.maxBumps()));
		}
		if (bumpedAt != null && now.isBefore(bumpedAt.plus(rules.cooldown()))) {
			Instant nextAllowedAt = bumpedAt.plus(rules.cooldown());
			throw new RateLimitExceededException(ErrorCodes.BUMP_TOO_SOON,
					"The listing can be bumped again at " + nextAllowedAt + ".", Duration.between(now, nextAllowedAt),
					Map.of("nextAllowedAt", nextAllowedAt.toString()));
		}
		bumpedAt = truncated(now);
		bumpCount++;
		touch(now);
	}

	private void applyContent(short newCategoryId, ListingContent content, ListingLocation location) {
		categoryId = newCategoryId;
		title = content.title().value();
		description = content.description().value();
		condition = content.condition();
		price = content.price().amountVnd();
		acceptOffers = content.acceptOffers();
		meetingSpotText = content.meetingSpot().value();
		exactLocation = location.pin().toPoint();
		publicLocation = location.publicPoint().toPoint();
	}

	/**
	 * The exact pin is kept only until the listing is deleted (SRS table
	 * {@code tab:retention}); the public point replaces it.
	 */
	private void markDeleted(Instant now) {
		exactLocation = publicLocation;
		deletedAt = truncated(now);
	}

	private void moveToHidden(ListingStatus hidden, HiddenReason reason, Instant now) {
		status = hidden;
		hiddenReason = reason;
		touch(now);
	}

	private void moveToVisible(ListingStatus target, Instant now) {
		status = target;
		hiddenReason = null;
		touch(now);
	}

	private void requireHiddenFor(HiddenReason reason, String action) {
		if (status == ListingStatus.HIDDEN && hiddenReason != reason) {
			throw new ConflictException(ErrorCodes.INVALID_LISTING_STATE,
					"A listing hidden for " + hiddenReason + " cannot be " + action + ".",
					Map.of("status", status.name(), "hiddenReason", String.valueOf(hiddenReason)));
		}
	}

	private void requireHeldBy(UUID transactionId) {
		if (status == ListingStatus.RESERVED && !transactionId.equals(reservedTransactionId)) {
			throw new ConflictException(ErrorCodes.INVALID_LISTING_STATE, "The listing is held by another transaction.",
					Map.of("status", status.name()));
		}
	}

	private ConflictException heldByTransaction(String action) {
		return new ConflictException(ErrorCodes.LISTING_HAS_OPEN_TRANSACTIONS,
				"Cannot " + action + " a listing held by a transaction.",
				Map.of("transactionId", String.valueOf(reservedTransactionId)));
	}

	private void touch(Instant now) {
		updatedAt = truncated(now);
	}

	/**
	 * Cursor pagination encodes {@code feed_at} in milliseconds; more precision would
	 * make a cursor skip listings created within the same millisecond.
	 */
	private static Instant truncated(Instant instant) {
		return instant.truncatedTo(ChronoUnit.MILLIS);
	}

	/**
	 * What a seller submits on A12 (UC-LS-01), after the gate, pin and quota checks.
	 *
	 * @param localityName copy of the area's public label for result cards (D9)
	 */
	public record NewListing(UUID sellerId, UUID areaId, short categoryId, ListingContent content,
			ListingLocation location, @Nullable String localityName) {
	}

	/**
	 * An edit on A12 (UC-LS-02): everything but the area; the pin stays inside the same
	 * area.
	 */
	public record ListingChanges(short categoryId, ListingContent content, ListingLocation location) {
	}

}
