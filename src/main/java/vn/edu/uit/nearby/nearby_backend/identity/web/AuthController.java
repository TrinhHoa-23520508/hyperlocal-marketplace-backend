package vn.edu.uit.nearby.nearby_backend.identity.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.AccountEmailService;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.PasswordResetService;
import vn.edu.uit.nearby.nearby_backend.identity.application.account.RegistrationService;
import vn.edu.uit.nearby.nearby_backend.identity.application.auth.AuthService;
import vn.edu.uit.nearby.nearby_backend.identity.application.auth.LoginCommand;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.ForgotPasswordRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.LoginRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.LogoutRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.OtpChallengeResponse;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.PasswordResetStartedResponse;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.PasswordResetTokenResponse;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.RefreshTokenRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.RegisterRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.RegistrationResponse;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.ResetPasswordRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.SendOtpRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.TokenResponse;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.VerifyEmailRequest;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.VerifyResetCodeRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;

@Tag(name = "Authentication",
		description = "Phone registration with SMS codes, login, tokens, password reset and email verification")
@SecurityRequirements
@RestController
@RequestMapping(ApiPaths.AUTH_BASE)
public class AuthController {

	private final RegistrationService registrationService;

	private final AuthService authService;

	private final PasswordResetService passwordResetService;

	private final AccountEmailService accountEmailService;

	public AuthController(RegistrationService registrationService, AuthService authService,
			PasswordResetService passwordResetService, AccountEmailService accountEmailService) {
		this.registrationService = registrationService;
		this.authService = authService;
		this.passwordResetService = passwordResetService;
		this.accountEmailService = accountEmailService;
	}

	@Operation(summary = "Send the 6-digit registration code by SMS (FR-ID-02)")
	@PostMapping("/register/otp")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public OtpChallengeResponse sendRegistrationCode(@Valid @RequestBody SendOtpRequest request) {
		return OtpChallengeResponse.from(registrationService.sendRegistrationCode(request.phone()));
	}

	@Operation(summary = "Register with the phone number, the SMS code and the accepted policies (FR-ID-01)")
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegistrationResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
		return RegistrationResponse.from(registrationService.register(request.toCommand(http.getRemoteAddr())));
	}

	@Operation(summary = "Log in with the phone number or a verified email (FR-ID-03)")
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return TokenResponse.from(authService.login(new LoginCommand(request.identifier(), request.password())));
	}

	@Operation(summary = "Exchange a refresh token for a new token pair; the old refresh token is revoked (FR-ID-04)")
	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return TokenResponse.from(authService.refresh(request.refreshToken()));
	}

	@Operation(summary = "Log out by revoking the refresh token (FR-ID-05)")
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
	}

	@Operation(summary = "Start a password reset by SMS code or email link; same answer whether or not the account "
			+ "exists (FR-ID-07)")
	@PostMapping("/password/forgot")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public PasswordResetStartedResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		return PasswordResetStartedResponse.from(passwordResetService.requestReset(request.identifier()));
	}

	@Operation(summary = "Exchange the SMS reset code for a single-use reset token (FR-ID-07)")
	@PostMapping("/password/verify-otp")
	public PasswordResetTokenResponse verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
		return PasswordResetTokenResponse
			.from(passwordResetService.verifyResetCode(request.challengeId(), request.phone(), request.otp()));
	}

	@Operation(summary = "Set a new password with the reset token from the SMS step or the email link; logs out "
			+ "every session")
	@PostMapping("/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		passwordResetService.resetPassword(request.resetToken(), request.newPassword());
	}

	@Operation(summary = "Verify the email address with the token from the verification link (FR-ID-09)")
	@PostMapping("/email-verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		accountEmailService.verifyEmail(request.token());
	}

}
