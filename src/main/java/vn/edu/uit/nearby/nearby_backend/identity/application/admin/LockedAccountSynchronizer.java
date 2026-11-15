package vn.edu.uit.nearby.nearby_backend.identity.application.admin;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import vn.edu.uit.nearby.nearby_backend.identity.SanctionLifted;
import vn.edu.uit.nearby.nearby_backend.identity.UserSanctioned;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.LockedAccountRegistry;

/**
 * Updates the locked-account registry only after the lock is committed, so a rolled-back
 * lock never blocks the user's tokens.
 */
@Component
public class LockedAccountSynchronizer {

	private final LockedAccountRegistry lockedAccounts;

	public LockedAccountSynchronizer(LockedAccountRegistry lockedAccounts) {
		this.lockedAccounts = lockedAccounts;
	}

	@TransactionalEventListener
	void onUserSanctioned(UserSanctioned event) {
		lockedAccounts.markLocked(event.userId());
	}

	@TransactionalEventListener
	void onSanctionLifted(SanctionLifted event) {
		lockedAccounts.clear(event.userId());
	}

}
