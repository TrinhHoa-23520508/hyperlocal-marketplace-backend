package vn.edu.uit.nearby.nearby_backend.identity.application.consent;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.LocationConsentWithdrawn;
import vn.edu.uit.nearby.nearby_backend.identity.LocationConsents;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParameters;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentChannel;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentGrant;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.UserConsent;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserConsentRepository;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

@Service
public class LocationConsentServiceImpl implements LocationConsentService, LocationConsents {

	private final UserRepository users;

	private final UserConsentRepository consents;

	private final IdentityParameters parameters;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public LocationConsentServiceImpl(UserRepository users, UserConsentRepository consents,
			IdentityParameters parameters, ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.consents = consents;
		this.parameters = parameters;
		this.events = events;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasConsented(UUID userId) {
		String currentTermsVersion = parameters.locationTermsVersion();
		return currentConsent(userId).filter(consent -> consent.isActiveFor(currentTermsVersion)).isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public LocationConsentStatus getConsent(UUID userId) {
		users.getRequired(userId);
		return statusOf(currentConsent(userId), parameters.locationTermsVersion());
	}

	@Override
	@Transactional
	public LocationConsentStatus grantConsent(UUID userId, String termsVersion) {
		String currentTermsVersion = parameters.locationTermsVersion();
		if (!currentTermsVersion.equals(termsVersion)) {
			throw new InvalidRequestException(ErrorCodes.OUTDATED_TERMS_VERSION,
					"Consent must be given to the current terms version " + currentTermsVersion + ".");
		}
		users.getRequired(userId);
		Instant now = clock.instant();
		currentConsent(userId).ifPresent(previous -> previous.withdraw(now));
		UserConsent granted = consents.save(UserConsent.grant(userId,
				new ConsentGrant(ConsentPurpose.PRECISE_LOCATION, termsVersion, ConsentChannel.ANDROID), now));
		return statusOf(Optional.of(granted), currentTermsVersion);
	}

	@Override
	@Transactional
	public void withdrawConsent(UUID userId) {
		users.getRequired(userId);
		Optional<UserConsent> active = currentConsent(userId).filter(UserConsent::isActive);
		if (active.isEmpty()) {
			return;
		}
		Instant now = clock.instant();
		active.get().withdraw(now);
		events.publishEvent(new LocationConsentWithdrawn(userId, now));
	}

	private Optional<UserConsent> currentConsent(UUID userId) {
		return consents.findCurrent(userId, ConsentPurpose.PRECISE_LOCATION);
	}

	private static LocationConsentStatus statusOf(Optional<UserConsent> consent, String currentTermsVersion) {
		return new LocationConsentStatus(consent.filter(c -> c.isActiveFor(currentTermsVersion)).isPresent(),
				consent.map(UserConsent::getPolicyVersion).orElse(null),
				consent.map(UserConsent::getGrantedAt).orElse(null),
				consent.map(UserConsent::getWithdrawnAt).orElse(null), currentTermsVersion);
	}

}
