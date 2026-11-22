package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.identity.application.consent.ConsentStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentPurpose;

public record ConsentResponse(ConsentPurpose purpose, boolean granted, @Nullable String policyVersion,
		@Nullable Instant grantedAt, @Nullable Instant withdrawnAt) {

	public static ConsentResponse from(ConsentStatus status) {
		return new ConsentResponse(status.purpose(), status.granted(), status.policyVersion(), status.grantedAt(),
				status.withdrawnAt());
	}

}
