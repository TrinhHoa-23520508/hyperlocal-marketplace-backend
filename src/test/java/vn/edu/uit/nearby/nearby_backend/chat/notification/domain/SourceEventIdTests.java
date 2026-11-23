package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SourceEventIdTests {

	private static final UUID DNS_NAMESPACE = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

	@Test
	@DisplayName("the name-based algorithm matches the known RFC 4122 vector for python.org")
	void followsRfc4122Version5() {
		assertThat(SourceEventId.nameBased(DNS_NAMESPACE, "python.org"))
			.isEqualTo(UUID.fromString("886313e1-3b8a-5372-9b90-0c9aee199e5d"));
	}

	@Test
	@DisplayName("FR-NT-04: the same event gives the same id on every replay")
	void sameEventSameId() {
		UUID area = UUID.randomUUID();
		Instant expiresAt = Instant.parse("2026-12-01T00:00:00Z");

		assertThat(SourceEventId.of("AreaExpiring", area, expiresAt))
			.isEqualTo(SourceEventId.of("AreaExpiring", area, expiresAt));
	}

	@Test
	void anotherEventGivesAnotherId() {
		UUID area = UUID.randomUUID();
		Instant expiresAt = Instant.parse("2026-12-01T00:00:00Z");

		assertThat(SourceEventId.of("AreaExpiring", area, expiresAt))
			.isNotEqualTo(SourceEventId.of("AreaExpired", area, expiresAt))
			.isNotEqualTo(SourceEventId.of("AreaExpiring", area, expiresAt.plusSeconds(1)))
			.isNotEqualTo(SourceEventId.of("AreaExpiring", UUID.randomUUID(), expiresAt));
	}

	@Test
	void theIdIsAVersion5Uuid() {
		UUID id = SourceEventId.of("MessageSent", 42L);

		assertThat(id.version()).isEqualTo(5);
		assertThat(id.variant()).isEqualTo(2);
	}

	@Test
	void fieldsAreSeparatedSoTheyCannotBlurTogether() {
		assertThat(SourceEventId.of("E", "ab", "c")).isNotEqualTo(SourceEventId.of("E", "a", "bc"));
	}

}
