package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.identity.application.account.RegisteredAccount;

public record RegistrationResponse(UUID userId, String phone, TokenResponse tokens) {

	public static RegistrationResponse from(RegisteredAccount account) {
		return new RegistrationResponse(account.userId(), account.phone(), TokenResponse.from(account.tokens()));
	}

}
