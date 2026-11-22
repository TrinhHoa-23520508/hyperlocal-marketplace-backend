package vn.edu.uit.nearby.nearby_backend.identity.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.AccountDeletionService;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.AccountEmailService;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.PasswordChangeService;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.PasswordChangeService.ChangePasswordCommand;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.AccountDeletionResponse;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.ChangeEmailRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.ChangePasswordRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.DeleteAccountRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;

@Tag(name = "Account security",
		description = "The current user's password, optional email address and account deletion")
@RestController
@RequestMapping(ApiPaths.CURRENT_USER_BASE)
public class AccountSecurityController {

	private final PasswordChangeService passwordChangeService;

	private final AccountEmailService accountEmailService;

	private final AccountDeletionService accountDeletionService;

	public AccountSecurityController(PasswordChangeService passwordChangeService,
			AccountEmailService accountEmailService, AccountDeletionService accountDeletionService) {
		this.passwordChangeService = passwordChangeService;
		this.accountEmailService = accountEmailService;
		this.accountDeletionService = accountDeletionService;
	}

	@Operation(summary = "Change the password; other devices are logged out, this one stays (FR-ID-08)")
	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "400", description = "CURRENT_PASSWORD_INVALID | PASSWORD_REUSED | WEAK_PASSWORD")
	public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		passwordChangeService.changePassword(new ChangePasswordCommand(CurrentUser.requireId(),
				CurrentUser.sessionId().orElse(null), request.currentPassword(), request.newPassword()));
	}

	@Operation(summary = "Add or replace the optional email address and send a verification link (FR-ID-09)")
	@PutMapping("/email")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@ApiResponse(responseCode = "409", description = "EMAIL_UNAVAILABLE: another account uses the address")
	public void changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
		accountEmailService.changeEmail(CurrentUser.requireId(), request.email());
	}

	@Operation(summary = "Send a new verification link to the unverified email address (FR-ID-09)")
	@PostMapping("/email/verification")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@ApiResponse(responseCode = "400", description = "EMAIL_NOT_SET: the account has no email")
	public void resendEmailVerification() {
		accountEmailService.resendVerification(CurrentUser.requireId());
	}

	@Operation(summary = "Delete the account: anonymised after the grace period unless the user logs in again "
			+ "(FR-ID-16)")
	@DeleteMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@ApiResponse(responseCode = "400", description = "CURRENT_PASSWORD_INVALID")
	@ApiResponse(responseCode = "403", description = "CANNOT_DELETE_ADMIN")
	@ApiResponse(responseCode = "409",
			description = "ACCOUNT_DELETION_BLOCKED (blockers lists open obligations) | ACCOUNT_STATUS_CONFLICT")
	public AccountDeletionResponse deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
		return AccountDeletionResponse
			.pendingUntil(accountDeletionService.requestDeletion(CurrentUser.requireId(), request.password()));
	}

}
