package vn.edu.uit.nearby.nearby_backend.areaverification.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaManagementService;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaVerificationService;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerifyAreaCommand;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaLabel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevel;
import vn.edu.uit.nearby.nearby_backend.areaverification.web.dto.AreaResponse;
import vn.edu.uit.nearby.nearby_backend.areaverification.web.dto.AreaVerificationResponse;
import vn.edu.uit.nearby.nearby_backend.areaverification.web.dto.RenameAreaRequest;
import vn.edu.uit.nearby.nearby_backend.areaverification.web.dto.RenewAreaRequest;
import vn.edu.uit.nearby.nearby_backend.areaverification.web.dto.SelectActiveAreaRequest;
import vn.edu.uit.nearby.nearby_backend.areaverification.web.dto.VerifyAreaRequest;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ApiPaths;
import vn.edu.uit.nearby.nearby_backend.shared.constant.HeaderNames;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;
import vn.edu.uit.nearby.nearby_backend.shared.web.idempotency.Idempotent;

@Tag(name = "Areas", description = "GPS-verified activity areas (FR-AR-01..09; screens A27, A28)")
@RestController
@RequestMapping(ApiPaths.AREAS_BASE)
public class AreaController {

	private static final String IDEMPOTENCY_KEY_DESCRIPTION = "UUID; resending the same key and body returns the "
			+ "stored response";

	private final AreaVerificationService verification;

	private final AreaManagementService management;

	public AreaController(AreaVerificationService verification, AreaManagementService management) {
		this.verification = verification;
		this.management = management;
	}

	@Operation(summary = "The caller's areas in slot order (A27)")
	@GetMapping("/me")
	public List<AreaResponse> listMyAreas() {
		return management.listAreas(CurrentUser.requireId()).stream().map(AreaResponse::from).toList();
	}

	@Operation(summary = "Verify a new area, or replace one, with a fresh GPS fix (UC-AR-01, UC-AR-03)",
			description = "201 creates an area. 200 means the fix was within 1 500 m of an existing area, "
					+ "which was renewed instead (UC-AR-01 A4).")
	@Idempotent
	@PostMapping("/verifications")
	@ApiResponse(responseCode = "201", description = "A new area was verified")
	@ApiResponse(responseCode = "200", description = "An existing area was renewed instead")
	@ApiResponse(responseCode = "403",
			description = "LOCATION_CONSENT_REQUIRED: no PRECISE_LOCATION consent | ACCESS_DENIED: the account "
					+ "is not ACTIVE or has no verified phone")
	@ApiResponse(responseCode = "404", description = "AREA_NOT_FOUND: replaceAreaId is not one of the caller's areas")
	@ApiResponse(responseCode = "409",
			description = "AREA_LIMIT_REACHED: already 2 areas; areaIds lists them for UC-AR-03 | "
					+ "AREA_HAS_OPEN_TRANSACTIONS: the replaced area's listings have open transactions | "
					+ "AREA_STATUS_CONFLICT: the replaced area was removed | IDEMPOTENCY_CONFLICT | DUPLICATE_REQUEST")
	@ApiResponse(responseCode = "422",
			description = "AREA_VERIFICATION_REJECTED: reasons MOCK_LOCATION, LOW_ACCURACY, STALE_FIX, "
					+ "IMPOSSIBLE_SPEED with guidance | OUT_OF_RENEW_RADIUS: when renewing a nearby area")
	@ApiResponse(responseCode = "429",
			description = "AREA_RATE_LIMITED: reason DAILY_ATTEMPTS (5 a day) or AREA_CHANGES (3 per 30 days); "
					+ "Retry-After header")
	public ResponseEntity<AreaVerificationResponse> verify(@Parameter(
			description = IDEMPOTENCY_KEY_DESCRIPTION) @RequestHeader(HeaderNames.IDEMPOTENCY_KEY) UUID idempotencyKey,
			@Valid @RequestBody VerifyAreaRequest request) {
		AreaLabel label = request.label() == null ? null : new AreaLabel(request.label());
		VerifyAreaCommand command = new VerifyAreaCommand(CurrentUser.requireId(), request.toSubmission(idempotencyKey),
				label, request.replaceAreaId());
		return VerificationResponses.toResponse(verification.verify(command));
	}

	@Operation(summary = "Renew an area with a fresh GPS fix within 1 500 m of its center (UC-AR-02)")
	@Idempotent
	@PostMapping("/{areaId}/renewals")
	@ApiResponse(responseCode = "403",
			description = "LOCATION_CONSENT_REQUIRED | ACCESS_DENIED: the account is not ACTIVE or has no "
					+ "verified phone")
	@ApiResponse(responseCode = "404", description = "AREA_NOT_FOUND: not one of the caller's areas")
	@ApiResponse(responseCode = "409",
			description = "AREA_STATUS_CONFLICT: the area was removed | IDEMPOTENCY_CONFLICT | DUPLICATE_REQUEST")
	@ApiResponse(responseCode = "422",
			description = "OUT_OF_RENEW_RADIUS: farther than 1 500 m; distanceMeters rounded to 100 m | "
					+ "AREA_VERIFICATION_REJECTED: hard rule broken; reasons with guidance")
	@ApiResponse(responseCode = "429", description = "AREA_RATE_LIMITED: reason DAILY_ATTEMPTS; Retry-After header")
	public ResponseEntity<AreaVerificationResponse> renew(@Parameter(
			description = IDEMPOTENCY_KEY_DESCRIPTION) @RequestHeader(HeaderNames.IDEMPOTENCY_KEY) UUID idempotencyKey,
			@PathVariable UUID areaId, @Valid @RequestBody RenewAreaRequest request) {
		return VerificationResponses
			.toResponse(verification.renew(CurrentUser.requireId(), areaId, request.toSubmission(idempotencyKey)));
	}

	@Operation(summary = "Name an area (UC-AR-01 step 9)")
	@PatchMapping("/{areaId}")
	@ApiResponse(responseCode = "404", description = "AREA_NOT_FOUND: not one of the caller's areas")
	@ApiResponse(responseCode = "409", description = "AREA_STATUS_CONFLICT: the area was removed")
	public AreaResponse rename(@PathVariable UUID areaId, @Valid @RequestBody RenameAreaRequest request) {
		return AreaResponse.from(management.rename(CurrentUser.requireId(), areaId, new AreaLabel(request.label())));
	}

	@Operation(summary = "Choose the area that drives the feed and its range (UC-AR-04)")
	@PutMapping("/active")
	@ApiResponse(responseCode = "400", description = "INVALID_RANGE: rangeLevel is not NEAR, MEDIUM or FAR")
	@ApiResponse(responseCode = "404", description = "AREA_NOT_FOUND: not one of the caller's areas")
	@ApiResponse(responseCode = "409", description = "AREA_STATUS_CONFLICT: only a VERIFIED or EXPIRING area")
	public AreaResponse selectActive(@Valid @RequestBody SelectActiveAreaRequest request) {
		return AreaResponse.from(management.selectActive(CurrentUser.requireId(), request.areaId(),
				RangeLevel.parse(request.rangeLevel())));
	}

	@Operation(summary = "Remove an area; its listings are hidden (UC-AR-03, FR-AR-07)")
	@DeleteMapping("/{areaId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "404", description = "AREA_NOT_FOUND: not one of the caller's areas")
	@ApiResponse(responseCode = "409", description = "AREA_HAS_OPEN_TRANSACTIONS: listingIds have open transactions | "
			+ "AREA_STATUS_CONFLICT: already removed")
	public void remove(@PathVariable UUID areaId) {
		management.remove(CurrentUser.requireId(), areaId);
	}

}
