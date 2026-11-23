package vn.edu.uit.nearby.nearby_backend.identity.application.consent;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.MarketingConsent;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParameters;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentChannel;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentGrant;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.UserConsent;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserConsentRepository;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;

/**
 * Consent per purpose (FR-ID-15). Every grant adds a row and the newest row of a purpose
 * decides. Marketing pushes are covered by the Privacy Policy version.
 */
@Service
public class ConsentService implements MarketingConsent {

	private final UserRepository users;

	private final UserConsentRepository consents;

	private final IdentityParameters parameters;

	private final Clock clock;

	public ConsentService(UserRepository users, UserConsentRepository consents, IdentityParameters parameters,
			Clock clock) {
		this.users = users;
		this.consents = consents;
		this.parameters = parameters;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ConsentStatus> listConsents(UUID userId) {
		users.getRequired(userId);
		return Arrays.stream(ConsentPurpose.values())
			.map(purpose -> ConsentStatus.of(purpose, consents.findCurrent(userId, purpose).orElse(null)))
			.toList();
	}

	/**
	 * Granting again while the consent is active keeps the existing row.
	 */
	@Transactional
	public ConsentStatus grantPushMarketing(UUID userId) {
		users.getRequired(userId);
		UserConsent current = consents.findCurrent(userId, ConsentPurpose.PUSH_MARKETING)
			.filter(UserConsent::isActive)
			.orElseGet(() -> consents.save(UserConsent.grant(userId, new ConsentGrant(ConsentPurpose.PUSH_MARKETING,
					parameters.privacyVersion(), ConsentChannel.ANDROID), clock.instant())));
		return ConsentStatus.of(ConsentPurpose.PUSH_MARKETING, current);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean allowsPushMarketing(UUID userId) {
		return consents.findCurrent(userId, ConsentPurpose.PUSH_MARKETING).filter(UserConsent::isActive).isPresent();
	}

	@Transactional
	public void withdrawPushMarketing(UUID userId) {
		users.getRequired(userId);
		consents.findCurrent(userId, ConsentPurpose.PUSH_MARKETING)
			.ifPresent(consent -> consent.withdraw(clock.instant()));
	}

}
