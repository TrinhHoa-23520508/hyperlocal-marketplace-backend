package vn.edu.uit.nearby.nearby_backend.identity.application.admin;

import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;

/**
 * Account search and sanctions (FR-ID-19, FR-MD-05). Every sanction revokes all sessions,
 * writes the audit log and publishes {@code UserSanctioned}.
 */
public interface UserAdministrationService {

	/**
	 * Newest accounts first.
	 */
	CursorPage<AdminUserView> searchUsers(UserSearchCriteria criteria);

	AdminUserView getUser(UUID userId);

	/**
	 * Bans the account for good.
	 * @throws OperationNotAllowedException {@code CANNOT_LOCK_OWN_ACCOUNT} or
	 * {@code CANNOT_LOCK_ADMIN}
	 * @throws ConflictException {@code ACCOUNT_STATUS_CONFLICT} unless the account is
	 * ACTIVE or SUSPENDED
	 */
	AdminUserView lockUser(UUID adminId, UUID userId, String reason);

	/**
	 * Suspends the account for 1, 7 or 30 days; it becomes ACTIVE again by itself.
	 * @throws InvalidRequestException {@code INVALID_SUSPENSION_PERIOD}
	 * @throws OperationNotAllowedException {@code CANNOT_LOCK_OWN_ACCOUNT} or
	 * {@code CANNOT_LOCK_ADMIN}
	 * @throws ConflictException {@code ACCOUNT_STATUS_CONFLICT} unless the account is
	 * ACTIVE
	 */
	AdminUserView suspendUser(SuspendUserCommand command);

	/**
	 * Lifts a suspension or a ban and publishes {@code SanctionLifted}.
	 * @throws ConflictException {@code ACCOUNT_STATUS_CONFLICT} unless the account is
	 * SUSPENDED or BANNED
	 */
	AdminUserView unlockUser(UUID adminId, UUID userId, String reason);

}
