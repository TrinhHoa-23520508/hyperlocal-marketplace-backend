package vn.edu.uit.nearby.nearby_backend.areaverification.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome.Rejected;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome.Verified;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.RejectionReason;
import vn.edu.uit.nearby.nearby_backend.areaverification.web.dto.AreaResponse;
import vn.edu.uit.nearby.nearby_backend.areaverification.web.dto.AreaVerificationResponse;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

/**
 * Turns a verification outcome into HTTP. The rejection is thrown only here, after the
 * service committed the rejected attempt (FR-AR-10).
 */
final class VerificationResponses {

	private VerificationResponses() {
	}

	/**
	 * 201 for a new area, 200 for a renewed one.
	 * @throws UnprocessableRequestException {@code OUT_OF_RENEW_RADIUS} with
	 * {@code distanceMeters} (FR-AR-04), or {@code AREA_VERIFICATION_REJECTED} with
	 * {@code reasons} (FR-AR-05); both carry {@code attemptId} and {@code guidance}
	 */
	static ResponseEntity<AreaVerificationResponse> toResponse(VerificationOutcome outcome) {
		return switch (outcome) {
			case Verified verified -> ResponseEntity.status(verified.isRenewal() ? HttpStatus.OK : HttpStatus.CREATED)
				.body(new AreaVerificationResponse(AreaResponse.from(verified.area()), verified.isRenewal()));
			case Rejected rejected -> throw rejectionOf(rejected);
		};
	}

	private static UnprocessableRequestException rejectionOf(Rejected rejected) {
		List<Map<String, String>> guidance = rejected.reasons()
			.stream()
			.map(reason -> Map.of("reason", reason.name(), "message", RejectionGuidance.messageFor(reason)))
			.toList();
		if (rejected.roundedDistanceMeters().isPresent()) {
			return new UnprocessableRequestException(ErrorCodes.OUT_OF_RENEW_RADIUS,
					"The fix is too far from the area center to renew it.",
					Map.of("distanceMeters", rejected.roundedDistanceMeters().getAsLong(), "attemptId",
							rejected.attemptId(), "guidance", guidance));
		}
		return new UnprocessableRequestException(ErrorCodes.AREA_VERIFICATION_REJECTED,
				"The location could not be verified.",
				Map.of("reasons", rejected.reasons().stream().map(RejectionReason::name).toList(), "attemptId",
						rejected.attemptId(), "guidance", guidance));
	}

}
