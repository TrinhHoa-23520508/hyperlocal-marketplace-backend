package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A listing a user saved (FR-LS-12). The key (user, listing) makes saving twice keep one
 * row.
 */
@Getter
@Entity
@Table(name = "favorites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {

	@EmbeddedId
	private FavoriteId id;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Embeddable
	public record FavoriteId(@Column(name = "user_id") UUID userId,
			@Column(name = "listing_id") UUID listingId) implements Serializable {
	}

}
