package vn.edu.uit.nearby.nearby_backend.identity.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserConsentTests {

	private static final Instant NOW = Instant.parse("2026-11-14T03:00:00Z");

	private static final ConsentGrant TERMS = new ConsentGrant(ConsentPurpose.TERMS, "1.0", ConsentChannel.ANDROID);

	@Test
	void grantedConsentIsActiveForItsPolicyVersion() {
		UserConsent consent = UserConsent.grant(UUID.randomUUID(), TERMS, NOW);

		assertThat(consent.getPurpose()).isEqualTo(ConsentPurpose.TERMS);
		assertThat(consent.getGrantedAt()).isEqualTo(NOW);
		assertThat(consent.isActive()).isTrue();
		assertThat(consent.isActiveFor("1.0")).isTrue();
	}

	@Test
	void consentToAnOlderVersionIsNotActiveForTheCurrentOne() {
		assertThat(UserConsent.grant(UUID.randomUUID(), TERMS, NOW).isActiveFor("2.0")).isFalse();
	}

	@Test
	void withdrawnConsentKeepsTheFirstWithdrawalTime() {
		UserConsent consent = UserConsent.grant(UUID.randomUUID(), TERMS, NOW);

		consent.withdraw(NOW.plusSeconds(60));
		consent.withdraw(NOW.plusSeconds(120));

		assertThat(consent.isActive()).isFalse();
		assertThat(consent.getWithdrawnAt()).isEqualTo(NOW.plusSeconds(60));
	}

}
