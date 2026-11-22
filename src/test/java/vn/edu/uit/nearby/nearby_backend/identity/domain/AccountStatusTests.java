package vn.edu.uit.nearby.nearby_backend.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;

class AccountStatusTests {

	private static final Map<String, UnaryOperator<AccountStatus>> MOVES = Map.of("suspend",
			AccountStatus::afterSuspension, "ban", AccountStatus::afterBan, "lift", AccountStatus::afterSanctionLifted,
			"requestDeletion", AccountStatus::afterDeletionRequested, "cancelDeletion",
			AccountStatus::afterDeletionCancelled, "anonymise", AccountStatus::afterAnonymisation);

	private static final Map<String, Set<AccountStatus>> ALLOWED_SOURCES = Map.of("suspend",
			EnumSet.of(AccountStatus.ACTIVE), "ban", EnumSet.of(AccountStatus.ACTIVE, AccountStatus.SUSPENDED), "lift",
			EnumSet.of(AccountStatus.SUSPENDED, AccountStatus.BANNED), "requestDeletion",
			EnumSet.of(AccountStatus.ACTIVE), "cancelDeletion", EnumSet.of(AccountStatus.PENDING_DELETION), "anonymise",
			EnumSet.of(AccountStatus.PENDING_DELETION));

	static Stream<Arguments> allowedMoves() {
		return Stream.of(Arguments.of(AccountStatus.ACTIVE, "suspend", AccountStatus.SUSPENDED),
				Arguments.of(AccountStatus.ACTIVE, "ban", AccountStatus.BANNED),
				Arguments.of(AccountStatus.SUSPENDED, "ban", AccountStatus.BANNED),
				Arguments.of(AccountStatus.SUSPENDED, "lift", AccountStatus.ACTIVE),
				Arguments.of(AccountStatus.BANNED, "lift", AccountStatus.ACTIVE),
				Arguments.of(AccountStatus.ACTIVE, "requestDeletion", AccountStatus.PENDING_DELETION),
				Arguments.of(AccountStatus.PENDING_DELETION, "cancelDeletion", AccountStatus.ACTIVE),
				Arguments.of(AccountStatus.PENDING_DELETION, "anonymise", AccountStatus.DELETED));
	}

	static Stream<Arguments> forbiddenMoves() {
		return MOVES.keySet()
			.stream()
			.flatMap(move -> EnumSet.complementOf(EnumSet.copyOf(ALLOWED_SOURCES.get(move)))
				.stream()
				.map(source -> Arguments.of(source, move)));
	}

	@ParameterizedTest(name = "{0} --{1}--> {2}")
	@MethodSource("allowedMoves")
	void everyMoveOfTheStateTableIsAllowed(AccountStatus source, String move, AccountStatus expected) {
		assertThat(MOVES.get(move).apply(source)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} cannot {1}")
	@MethodSource("forbiddenMoves")
	void everyOtherMoveIsAConflict(AccountStatus source, String move) {
		assertThatThrownBy(() -> MOVES.get(move).apply(source)).isInstanceOf(ConflictException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("ACCOUNT_STATUS_CONFLICT");
	}

	@ParameterizedTest
	@EnumSource(AccountStatus.class)
	void onlySuspendedAndBannedAreSanctioned(AccountStatus status) {
		assertThat(status.isSanctioned())
			.isEqualTo(status == AccountStatus.SUSPENDED || status == AccountStatus.BANNED);
	}

}
