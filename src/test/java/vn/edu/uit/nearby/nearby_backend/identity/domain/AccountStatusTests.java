package vn.edu.uit.nearby.nearby_backend.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;

class AccountStatusTests {

	@Test
	void lockingAnActiveAccountBansIt() {
		assertThat(AccountStatus.ACTIVE.afterLock()).isEqualTo(AccountStatus.BANNED);
	}

	@Test
	void lockingABannedAccountIsAConflict() {
		assertThatThrownBy(AccountStatus.BANNED::afterLock).isInstanceOf(ConflictException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("ACCOUNT_ALREADY_LOCKED");
	}

	@Test
	void unlockingABannedAccountMakesItActive() {
		assertThat(AccountStatus.BANNED.afterUnlock()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void unlockingAnActiveAccountIsAConflict() {
		assertThatThrownBy(AccountStatus.ACTIVE::afterUnlock).isInstanceOf(ConflictException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("ACCOUNT_NOT_LOCKED");
	}

	@Test
	void activeAccountCanLogIn() {
		assertThatCode(AccountStatus.ACTIVE::ensureCanLogIn).doesNotThrowAnyException();
	}

	@Test
	void bannedAccountCannotLogIn() {
		assertThatThrownBy(AccountStatus.BANNED::ensureCanLogIn).isInstanceOf(OperationNotAllowedException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("ACCOUNT_LOCKED");
	}

	@Test
	void onlyBannedCountsAsLocked() {
		assertThat(AccountStatus.BANNED.isLocked()).isTrue();
		assertThat(AccountStatus.ACTIVE.isLocked()).isFalse();
	}

}
