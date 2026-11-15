package vn.edu.uit.nearby.nearby_backend.identity.domain;

import java.util.EnumSet;
import java.util.Set;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;

/**
 * Account state machine (SRS table 14, FR-ID-19). Every move not listed in the table is a
 * conflict.
 */
public enum AccountStatus {

	ACTIVE, SUSPENDED, BANNED, PENDING_DELETION, DELETED;

	public AccountStatus afterSuspension() {
		return moveTo(SUSPENDED, EnumSet.of(ACTIVE));
	}

	public AccountStatus afterBan() {
		return moveTo(BANNED, EnumSet.of(ACTIVE, SUSPENDED));
	}

	public AccountStatus afterSanctionLifted() {
		return moveTo(ACTIVE, EnumSet.of(SUSPENDED, BANNED));
	}

	public AccountStatus afterDeletionRequested() {
		return moveTo(PENDING_DELETION, EnumSet.of(ACTIVE));
	}

	public AccountStatus afterDeletionCancelled() {
		return moveTo(ACTIVE, EnumSet.of(PENDING_DELETION));
	}

	public AccountStatus afterAnonymisation() {
		return moveTo(DELETED, EnumSet.of(PENDING_DELETION));
	}

	public boolean isSanctioned() {
		return this == SUSPENDED || this == BANNED;
	}

	private AccountStatus moveTo(AccountStatus target, Set<AccountStatus> allowedSources) {
		if (!allowedSources.contains(this)) {
			throw new ConflictException(ErrorCodes.ACCOUNT_STATUS_CONFLICT,
					"An account that is " + this + " cannot become " + target + ".");
		}
		return target;
	}

}
