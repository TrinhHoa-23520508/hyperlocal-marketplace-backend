package vn.edu.uit.nearby.nearby_backend.identity.application.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import vn.edu.uit.nearby.nearby_backend.identity.LocationConsentWithdrawn;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParametersFixture;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentChannel;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentGrant;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.UserConsent;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserConsentRepository;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;

@ExtendWith(MockitoExtension.class)
class LocationConsentServiceImplTests {

	private static final String CURRENT_TERMS = "1.0";

	@Mock
	UserRepository users;

	@Mock
	UserConsentRepository consents;

	@Mock
	ApplicationEventPublisher events;

	private final User user = UserFixtures.activeUser();

	private LocationConsentServiceImpl consentService;

	@BeforeEach
	void createService() {
		consentService = new LocationConsentServiceImpl(users, consents, IdentityParametersFixture.seeded(), events,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void consentToTheCurrentTermsAddsALedgerRow() {
		when(users.getRequired(user.getId())).thenReturn(user);
		when(consents.save(any(UserConsent.class))).then(AdditionalAnswers.returnsFirstArg());

		LocationConsentStatus status = consentService.grantConsent(user.getId(), CURRENT_TERMS);

		assertThat(status.granted()).isTrue();
		assertThat(status.consentedAt()).isEqualTo(NOW);
		assertThat(status.termsVersion()).isEqualTo(CURRENT_TERMS);
		assertThat(status.currentTermsVersion()).isEqualTo(CURRENT_TERMS);
	}

	@Test
	void consentingAgainClosesThePreviousRow() {
		UserConsent previous = givenCurrentConsent("0.9");
		when(users.getRequired(user.getId())).thenReturn(user);
		when(consents.save(any(UserConsent.class))).then(AdditionalAnswers.returnsFirstArg());

		consentService.grantConsent(user.getId(), CURRENT_TERMS);

		assertThat(previous.getWithdrawnAt()).isEqualTo(NOW);
	}

	@Test
	void consentToAnOutdatedTermsVersionIsRejected() {
		assertThatThrownBy(() -> consentService.grantConsent(user.getId(), "0.9"))
			.isInstanceOf(InvalidRequestException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("OUTDATED_TERMS_VERSION");
	}

	@Test
	void statusOfAUserWhoNeverConsentedIsNotGranted() {
		when(users.getRequired(user.getId())).thenReturn(user);
		when(consents.findCurrent(user.getId(), ConsentPurpose.PRECISE_LOCATION)).thenReturn(Optional.empty());

		LocationConsentStatus status = consentService.getConsent(user.getId());

		assertThat(status.granted()).isFalse();
		assertThat(status.consentedAt()).isNull();
	}

	@Test
	void facadeReportsConsentOnlyForTheCurrentTermsVersion() {
		givenCurrentConsent("0.9");

		assertThat(consentService.hasConsented(user.getId())).isFalse();
	}

	@Test
	void unknownUserHasNotConsented() {
		UUID unknown = UUID.randomUUID();
		when(consents.findCurrent(unknown, ConsentPurpose.PRECISE_LOCATION)).thenReturn(Optional.empty());

		assertThat(consentService.hasConsented(unknown)).isFalse();
	}

	@Test
	void requireConsentBlocksUsersWithoutConsent() {
		when(consents.findCurrent(user.getId(), ConsentPurpose.PRECISE_LOCATION)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> consentService.requireConsent(user.getId()))
			.isInstanceOf(OperationNotAllowedException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("LOCATION_CONSENT_REQUIRED");
	}

	@Test
	void requireConsentLetsConsentedUsersThrough() {
		givenCurrentConsent(CURRENT_TERMS);

		assertThatCode(() -> consentService.requireConsent(user.getId())).doesNotThrowAnyException();
	}

	@Test
	void withdrawalStopsConsentAndNotifiesOtherModules() {
		UserConsent current = givenCurrentConsent(CURRENT_TERMS);
		when(users.getRequired(user.getId())).thenReturn(user);

		consentService.withdrawConsent(user.getId());

		assertThat(current.isActive()).isFalse();
		verify(events).publishEvent(new LocationConsentWithdrawn(user.getId(), NOW));
	}

	@Test
	void withdrawingWithoutAnActiveConsentDoesNothing() {
		when(users.getRequired(user.getId())).thenReturn(user);
		when(consents.findCurrent(user.getId(), ConsentPurpose.PRECISE_LOCATION)).thenReturn(Optional.empty());

		consentService.withdrawConsent(user.getId());

		verifyNoInteractions(events);
	}

	private UserConsent givenCurrentConsent(String version) {
		UserConsent consent = UserConsent.grant(user.getId(),
				new ConsentGrant(ConsentPurpose.PRECISE_LOCATION, version, ConsentChannel.ANDROID),
				NOW.minusSeconds(60));
		when(consents.findCurrent(user.getId(), ConsentPurpose.PRECISE_LOCATION)).thenReturn(Optional.of(consent));
		return consent;
	}

}
