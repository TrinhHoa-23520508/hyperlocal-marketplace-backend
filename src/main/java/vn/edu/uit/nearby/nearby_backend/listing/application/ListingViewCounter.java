package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;

/**
 * FR-LS-14 (DEC-LS-8): one view per viewer, listing and calendar day in Vietnam time,
 * de-duplicated with a Redis key. The only write of the discovery read path (FR-DC-12);
 * it runs in its own transaction on the primary and a failure never blocks the detail
 * screen.
 */
@Component
class ListingViewCounter {

	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

	/**
	 * A day plus an hour, so the key outlives the day it counts.
	 */
	private static final Duration KEY_TTL = Duration.ofHours(25);

	private final StringRedisTemplate redis;

	private final ListingRepository listings;

	private final Clock clock;

	ListingViewCounter(StringRedisTemplate redis, ListingRepository listings, Clock clock) {
		this.redis = redis;
		this.listings = listings;
		this.clock = clock;
	}

	/**
	 * @param viewerKey the user id, or a fingerprint of a guest
	 * @return whether this view was counted
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean countView(UUID listingId, String viewerKey) {
		String day = LocalDate.ofInstant(clock.instant(), VIETNAM).format(DAY);
		String key = RedisKeyPrefixes.LISTING_VIEWS + listingId + ":" + viewerKey + ":" + day;
		Boolean firstToday = redis.opsForValue().setIfAbsent(key, "1", KEY_TTL);
		if (!Boolean.TRUE.equals(firstToday)) {
			return false;
		}
		listings.incrementViewCount(listingId);
		return true;
	}

}
