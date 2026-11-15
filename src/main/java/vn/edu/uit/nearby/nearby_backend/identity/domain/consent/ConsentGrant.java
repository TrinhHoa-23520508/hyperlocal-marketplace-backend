package vn.edu.uit.nearby.nearby_backend.identity.domain.consent;

public record ConsentGrant(ConsentPurpose purpose, String policyVersion, ConsentChannel channel) {

}
