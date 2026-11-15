package vn.edu.uit.nearby.nearby_backend.identity.domain;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;

/**
 * Account state machine (SRS table 14): registration with a verified phone number creates
 * an ACTIVE account; ACTIVE --lock--> BANNED; BANNED --unlock--> ACTIVE.
 */
public enum AccountStatus {

	ACTIVE {
		@Override
		public void ensureCanLogIn() {
		}

		@Override
		public AccountStatus afterLock() {
			return BANNED;
		}

		@Override
		public AccountStatus afterUnlock() {
			throw new ConflictException(ErrorCodes.ACCOUNT_NOT_LOCKED, "The account is not locked.");
		}
	},

	BANNED {
		@Override
		public void ensureCanLogIn() {
			throw new OperationNotAllowedException(ErrorCodes.ACCOUNT_LOCKED, "This account cannot log in.");
		}

		@Override
		public AccountStatus afterLock() {
			throw new ConflictException(ErrorCodes.ACCOUNT_ALREADY_LOCKED, "The account is already locked.");
		}

		@Override
		public AccountStatus afterUnlock() {
			return ACTIVE;
		}
	};

	public abstract void ensureCanLogIn();

	public abstract AccountStatus afterLock();

	public abstract AccountStatus afterUnlock();

	public boolean isLocked() {
		return this == BANNED;
	}

}
