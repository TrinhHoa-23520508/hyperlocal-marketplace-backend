package vn.edu.uit.nearby.nearby_backend.identity.application.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParametersFixture;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentChannel;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentGrant;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.UserConsent;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserConsentRepository;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTests {

	private static final UUID USER_ID = UUID.randomUUID();

	private static final Instant LATER = NOW.plus(Duration.ofDays(1));

	@Mock
	UserRepository users;

	@Mock
	UserConsentRepository consents;

	@Test
	void everyPurposeIsListedWithItsNewestRow() {
		UserConsent terms = consent(ConsentPurpose.TERMS);
		UserConsent marketing = consent(ConsentPurpose.PUSH_MARKETING);
		marketing.withdraw(LATER);
		when(consents.findCurrent(any(), any())).thenReturn(Optional.empty());
		when(consents.findCurrent(USER_ID, ConsentPurpose.TERMS)).thenReturn(Optional.of(terms));
		when(consents.findCurrent(USER_ID, ConsentPurpose.PUSH_MARKETING)).thenReturn(Optional.of(marketing));

		List<ConsentStatus> statuses = service().listConsents(USER_ID);

		assertThat(statuses).containsExactly(new ConsentStatus(ConsentPurpose.TERMS, true, "1.0", NOW, null),
				new ConsentStatus(ConsentPurpose.PRIVACY, false, null, null, null),
				new ConsentStatus(ConsentPurpose.PRECISE_LOCATION, false, null, null, null),
				new ConsentStatus(ConsentPurpose.PUSH_MARKETING, false, "1.0", NOW, LATER));
	}

	@Test
	void grantingMarketingAddsARowWithThePrivacyPolicyVersion() {
		when(consents.findCurrent(USER_ID, ConsentPurpose.PUSH_MARKETING)).thenReturn(Optional.empty());
		when(consents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ConsentStatus status = service().grantPushMarketing(USER_ID);

		assertThat(status).isEqualTo(new ConsentStatus(ConsentPurpose.PUSH_MARKETING, true, "1.0", NOW, null));
	}

	@Test
	void grantingAgainWhileActiveKeepsTheExistingRow() {
		when(consents.findCurrent(USER_ID, ConsentPurpose.PUSH_MARKETING))
			.thenReturn(Optional.of(consent(ConsentPurpose.PUSH_MARKETING)));

		assertThat(service().grantPushMarketing(USER_ID).granted()).isTrue();
		verify(consents, never()).save(any());
	}

	@Test
	void withdrawingMarketingMarksTheNewestRow() {
		UserConsent marketing = consent(ConsentPurpose.PUSH_MARKETING);
		when(consents.findCurrent(USER_ID, ConsentPurpose.PUSH_MARKETING)).thenReturn(Optional.of(marketing));

		service().withdrawPushMarketing(USER_ID);

		assertThat(marketing.getWithdrawnAt()).isEqualTo(NOW);
	}

	@Test
	void withdrawingWithoutConsentChangesNothing() {
		when(consents.findCurrent(USER_ID, ConsentPurpose.PUSH_MARKETING)).thenReturn(Optional.empty());

		service().withdrawPushMarketing(USER_ID);

		verify(consents, never()).save(any());
	}

	private static UserConsent consent(ConsentPurpose purpose) {
		return UserConsent.grant(USER_ID, new ConsentGrant(purpose, "1.0", ConsentChannel.ANDROID), NOW);
	}

	private ConsentService service() {
		return new ConsentService(users, consents, IdentityParametersFixture.seeded(),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

}
