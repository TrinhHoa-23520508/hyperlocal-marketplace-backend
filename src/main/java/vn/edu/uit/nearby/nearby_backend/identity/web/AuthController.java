package vn.edu.uit.nearby.nearby_backend.identity.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
	@ApiResponse(responseCode = "400", description = "INVALID_PHONE: not a Vietnamese mobile number")
	@ApiResponse(responseCode = "429",
			description = "OTP_RATE_LIMITED: inside the 60 s resend interval or over 5 codes per day; see Retry-After")
	@ApiResponse(responseCode = "503",
			description = "SMS_UNAVAILABLE: the SMS gateway failed; the daily quota is not used")
	public OtpChallengeResponse sendRegistrationCode(@Valid @RequestBody SendOtpRequest request) {
		return OtpChallengeResponse.from(registrationService.sendRegistrationCode(request.phone()));
	}

	@Operation(summary = "Register with the phone number, the SMS code and the accepted policies (FR-ID-01)")
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "400", description = "WEAK_PASSWORD (violations lists every rule) | INVALID_PHONE")
	@ApiResponse(responseCode = "409",
			description = "PHONE_ALREADY_REGISTERED (only after a correct code) | POLICY_VERSION_STALE")
	@ApiResponse(responseCode = "422",
			description = "CONSENT_REQUIRED | OTP_INVALID (remainingAttempts) | OTP_EXPIRED | OTP_ATTEMPTS_EXCEEDED")
	@ApiResponse(responseCode = "429",
			description = "RATE_LIMITED: too many registrations from this IP; see Retry-After")
	public RegistrationResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
		return RegistrationResponse.from(registrationService.register(request.toCommand(http.getRemoteAddr())));
	}

	@Operation(summary = "Log in with the phone number or a verified email (FR-ID-03)")
	@PostMapping("/login")
	@ApiResponse(responseCode = "400",
			description = "INVALID_PHONE: the identifier is neither an email nor a Vietnamese mobile number")
	@ApiResponse(responseCode = "401",
			description = "INVALID_CREDENTIALS: unknown identifier, unverified email or wrong password (identical answers)")
	@ApiResponse(responseCode = "403",
			description = "ACCOUNT_SUSPENDED (reason, suspendedUntil) | ACCOUNT_BANNED (reason)")
	@ApiResponse(responseCode = "429",
			description = "LOGIN_LOCKED: 5 failures for the identifier or 20 from the IP in 15 min; see Retry-After")
	public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return TokenResponse
			.from(authService.login(new LoginCommand(request.identifier(), request.password(), http.getRemoteAddr())));
	}

	@Operation(summary = "Exchange a refresh token for a new token pair; the old refresh token is revoked (FR-ID-04)")
	@PostMapping("/refresh")
	@ApiResponse(responseCode = "401",
			description = "INVALID_REFRESH_TOKEN | REFRESH_TOKEN_REUSED (the whole session is revoked)")
	@ApiResponse(responseCode = "403", description = "ACCOUNT_SUSPENDED | ACCOUNT_BANNED")
	public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return TokenResponse.from(authService.refresh(request.refreshToken()));
	}

	@Operation(summary = "Log out by revoking the refresh token and dropping the device's push token (FR-ID-05)")
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken(), request.fcmToken());
	}

	@Operation(summary = "Start a password reset by SMS code or email link; same answer whether or not the account "
			+ "exists (FR-ID-07)")
	@PostMapping("/password/forgot")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@ApiResponse(responseCode = "400", description = "INVALID_PHONE")
	@ApiResponse(responseCode = "429", description = "OTP_RATE_LIMITED; see Retry-After")
	@ApiResponse(responseCode = "503", description = "SMS_UNAVAILABLE")
	public PasswordResetStartedResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		return PasswordResetStartedResponse.from(passwordResetService.requestReset(request.identifier()));
	}

	@Operation(summary = "Exchange the SMS reset code for a single-use reset token (FR-ID-07)")
	@PostMapping("/password/verify-otp")
	@ApiResponse(responseCode = "422",
			description = "OTP_INVALID (remainingAttempts) | OTP_EXPIRED | OTP_ATTEMPTS_EXCEEDED")
	public PasswordResetTokenResponse verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
		return PasswordResetTokenResponse
			.from(passwordResetService.verifyResetCode(request.challengeId(), request.phone(), request.otp()));
	}

	@Operation(summary = "Set a new password with the reset token from the SMS step or the email link; logs out "
			+ "every session")
	@PostMapping("/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "400", description = "WEAK_PASSWORD: the reset token stays valid")
	@ApiResponse(responseCode = "422", description = "RESET_TOKEN_INVALID: unknown, used or expired")
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		passwordResetService.resetPassword(request.resetToken(), request.newPassword());
	}

	@Operation(summary = "Verify the email address with the token from the verification link (FR-ID-09)")
	@PostMapping("/email-verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "422",
			description = "INVALID_VERIFICATION_TOKEN: unknown, used or expired, or the email changed since")
	public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		accountEmailService.verifyEmail(request.token());
	}

}
