package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.aVerifiedArea;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaRemovalBlocker;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;

class AreaRemovalGuardTests {

	@Test
	@DisplayName("FR-AR-07: with no module blocking, an area can be removed")
	void nothingBlocksWithoutBlockers() {
		assertThatCode(() -> new AreaRemovalGuard(List.of()).requireRemovable(aVerifiedArea()))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("FR-AR-07: blockers that report nothing let the area go")
	void emptyAnswersDoNotBlock() {
		AreaRemovalBlocker noOpenTransactions = (userId, areaId) -> List.of();

		assertThatCode(() -> new AreaRemovalGuard(List.of(noOpenTransactions)).requireRemovable(aVerifiedArea()))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("FR-AR-07: open transactions block the removal and name every listing once")
	void openTransactionsBlockWithTheirListings() {
		UserArea area = aVerifiedArea();
		UUID shared = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		AreaRemovalBlocker offers = (userId, areaId) -> List.of(shared);
		AreaRemovalBlocker payments = (userId, areaId) -> List.of(shared, other);

		assertThatThrownBy(() -> new AreaRemovalGuard(List.of(offers, payments)).requireRemovable(area))
			.isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "AREA_HAS_OPEN_TRANSACTIONS")
			.satisfies(ex -> assertThat(((BusinessException) ex).getProperties().get("listingIds"))
				.isEqualTo(List.of(shared, other)));
	}

	@Test
	@DisplayName("FR-AR-07: the blocker is asked about the right user and area")
	void blockerReceivesTheAreaOwnerAndId() {
		UserArea area = aVerifiedArea();
		AreaRemovalBlocker strict = (userId, areaId) -> userId.equals(area.getUserId()) && areaId.equals(area.getId())
				? List.of(UUID.randomUUID()) : List.of();

		assertThatThrownBy(() -> new AreaRemovalGuard(List.of(strict)).requireRemovable(area))
			.isInstanceOf(ConflictException.class);
	}

}
