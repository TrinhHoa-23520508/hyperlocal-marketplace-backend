package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.time.Instant;

import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;

/**
 * @param deleteAfter logging in before this time cancels the deletion
 */
public record AccountDeletionResponse(AccountStatus status, Instant deleteAfter) {

	public static AccountDeletionResponse pendingUntil(Instant deleteAfter) {
		return new AccountDeletionResponse(AccountStatus.PENDING_DELETION, deleteAfter);
	}

}
