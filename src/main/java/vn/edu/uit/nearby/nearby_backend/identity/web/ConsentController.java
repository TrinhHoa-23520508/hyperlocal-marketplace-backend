package vn.edu.uit.nearby.nearby_backend.identity.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.ConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.web.dto.ConsentResponse;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;

@Tag(name = "Consents", description = "Consent per purpose and marketing pushes (FR-ID-15)")
@RestController
@RequestMapping(ApiPaths.CURRENT_USER_BASE + "/consents")
public class ConsentController {

	private final ConsentService consentService;

	public ConsentController(ConsentService consentService) {
		this.consentService = consentService;
	}

	@Operation(summary = "Current consent for every purpose: TERMS, PRIVACY, PRECISE_LOCATION, PUSH_MARKETING")
	@GetMapping
	public List<ConsentResponse> listConsents() {
		return consentService.listConsents(CurrentUser.requireId()).stream().map(ConsentResponse::from).toList();
	}

	@Operation(summary = "Agree to marketing pushes")
	@PutMapping("/push-marketing")
	public ConsentResponse grantPushMarketing() {
		return ConsentResponse.from(consentService.grantPushMarketing(CurrentUser.requireId()));
	}

	@Operation(summary = "Withdraw consent to marketing pushes")
	@DeleteMapping("/push-marketing")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdrawPushMarketing() {
		consentService.withdrawPushMarketing(CurrentUser.requireId());
	}

}
