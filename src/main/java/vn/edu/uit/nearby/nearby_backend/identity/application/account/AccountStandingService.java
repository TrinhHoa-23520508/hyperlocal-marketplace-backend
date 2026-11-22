package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.AccountStanding;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;

@Service
public class AccountStandingService implements AccountStanding {

	private final UserRepository users;

	public AccountStandingService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isActiveWithVerifiedPhone(UUID userId) {
		return users.findById(userId)
			.filter(user -> user.getStatus() == AccountStatus.ACTIVE && user.hasVerifiedPhone())
			.isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public void requireActiveWithVerifiedPhone(UUID userId) {
		if (!isActiveWithVerifiedPhone(userId)) {
			throw new OperationNotAllowedException(ErrorCodes.ACCESS_DENIED,
					"Only an active account with a verified phone number can do this.");
		}
	}

}
