package vn.edu.uit.nearby.nearby_backend.identity.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.AccountEmailService;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.PasswordChangeService;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.PasswordChangeService.ChangePasswordCommand;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.ChangeEmailRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.ChangePasswordRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;

@Tag(name = "Account security", description = "The current user's password and optional email address")
@RestController
@RequestMapping(ApiPaths.CURRENT_USER_BASE)
public class AccountSecurityController {

	private final PasswordChangeService passwordChangeService;

	private final AccountEmailService accountEmailService;

	public AccountSecurityController(PasswordChangeService passwordChangeService,
			AccountEmailService accountEmailService) {
		this.passwordChangeService = passwordChangeService;
		this.accountEmailService = accountEmailService;
	}

	@Operation(summary = "Change the password; other devices are logged out, this one stays (FR-ID-08)")
	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		passwordChangeService.changePassword(new ChangePasswordCommand(CurrentUser.requireId(),
				CurrentUser.sessionId().orElse(null), request.currentPassword(), request.newPassword()));
	}

	@Operation(summary = "Add or replace the optional email address and send a verification link (FR-ID-09)")
	@PutMapping("/email")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
		accountEmailService.changeEmail(CurrentUser.requireId(), request.email());
	}

	@Operation(summary = "Send a new verification link to the unverified email address (FR-ID-09)")
	@PostMapping("/email/verification")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void resendEmailVerification() {
		accountEmailService.resendVerification(CurrentUser.requireId());
	}

}
