package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Clock;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.AreaVerificationAttemptRepository;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AccountDeleted;

/**
 * Anonymisation of a deleted account (UC-SY-12, SRS section 4.5): its areas are deleted
 * and the precise fixes of its attempts are cleared. The attempts themselves stay as
 * evidence. Handling the same event twice changes nothing.
 */
@Component
class AreaAccountDeletionListener {

	private final UserAreaRepository areas;

	private final AreaVerificationAttemptRepository attempts;

	private final Clock clock;

	AreaAccountDeletionListener(UserAreaRepository areas, AreaVerificationAttemptRepository attempts, Clock clock) {
		this.areas = areas;
		this.attempts = attempts;
		this.clock = clock;
	}

	@ApplicationModuleListener
	void onAccountDeleted(AccountDeleted event) {
		attempts.purgeLocationsOfUser(event.userId(), clock.instant());
		areas.deleteAllOfUser(event.userId());
	}

}
