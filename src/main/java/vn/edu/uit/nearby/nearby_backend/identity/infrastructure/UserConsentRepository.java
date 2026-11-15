package vn.edu.uit.nearby.nearby_backend.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.ConsentPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.domain.consent.UserConsent;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

	Optional<UserConsent> findFirstByUserIdAndPurposeOrderByGrantedAtDesc(UUID userId, ConsentPurpose purpose);

	/**
	 * The newest row for the purpose is the one that decides (FR-ID-15).
	 */
	default Optional<UserConsent> findCurrent(UUID userId, ConsentPurpose purpose) {
		return findFirstByUserIdAndPurposeOrderByGrantedAtDesc(userId, purpose);
	}

}
