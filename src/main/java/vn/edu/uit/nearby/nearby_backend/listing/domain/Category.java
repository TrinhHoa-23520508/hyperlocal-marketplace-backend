package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

/**
 * Item category, seeded by Flyway in R1 (admin management FR-LS-15 is R2). Existing
 * listings keep a category that was deactivated.
 */
@Getter
@Entity
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

	@Id
	private short id;

	@Column(nullable = false, updatable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(name = "icon_key")
	private @Nullable String iconKey;

	@Column(name = "sort_order", nullable = false)
	private short sortOrder;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	/**
	 * @throws UnprocessableRequestException {@code CATEGORY_INACTIVE} for a category that
	 * new listings can no longer use (FR-LS-15)
	 */
	public void requireSelectable() {
		if (!active) {
			throw new UnprocessableRequestException(ErrorCodes.CATEGORY_INACTIVE,
					"The category " + code + " is no longer available for new listings.");
		}
	}

}
