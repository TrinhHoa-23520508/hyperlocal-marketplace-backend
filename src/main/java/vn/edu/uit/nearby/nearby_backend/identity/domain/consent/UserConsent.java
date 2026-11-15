package vn.edu.uit.nearby.nearby_backend.identity.domain.consent;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One consent decision for one processing purpose (FR-ID-15, Law 91/2025/QH15). Rows are
 * never deleted: granting again adds a row, and the newest row per purpose decides.
 */
@Getter
@Entity
@Table(name = "user_consents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private ConsentPurpose purpose;

	@Column(name = "policy_version", nullable = false, updatable = false)
	private String policyVersion;

	@Column(name = "granted_at", nullable = false, updatable = false)
	private Instant grantedAt;

	@Column(name = "withdrawn_at")
	private @Nullable Instant withdrawnAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private ConsentChannel channel;

	private UserConsent(UUID userId, ConsentPurpose purpose, String policyVersion, ConsentChannel channel,
			Instant now) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.purpose = purpose;
		this.policyVersion = policyVersion;
		this.channel = channel;
		this.grantedAt = now;
	}

	public static UserConsent grant(UUID userId, ConsentGrant grant, Instant now) {
		return new UserConsent(userId, grant.purpose(), grant.policyVersion(), grant.channel(), now);
	}

	public boolean isActive() {
		return withdrawnAt == null;
	}

	public boolean isActiveFor(String currentPolicyVersion) {
		return isActive() && currentPolicyVersion.equals(policyVersion);
	}

	public void withdraw(Instant now) {
		if (withdrawnAt == null) {
			withdrawnAt = now;
		}
	}

}
