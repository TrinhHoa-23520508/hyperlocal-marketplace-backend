package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaRemoved;
import vn.edu.uit.nearby.nearby_backend.areaverification.AreaVerified;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AttemptRecorder.AttemptTarget;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AttemptRecorder.Evaluation;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome.Rejected;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome.Verified;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaLabel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RenewalRadius.RenewalCheck;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea.NewArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.VerificationKind;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.RejectionReason;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * Verifying, renewing and replacing areas on the server (ADR-04), following SRS appendix
 * D "Kiểm tra xác minh khu vực": preconditions, limits, hard rules and soft signals,
 * renewal radius, area creation, and an attempt record for every try.
 */
@Service
public class AreaVerificationService {

	private final UserAreaRepository areas;

	private final AttemptRecorder recorder;

	private final AttemptLimits limits;

	private final AreaRemovalGuard removalGuard;

	private final VerificationEligibility eligibility;

	private final AreaParameters parameters;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	AreaVerificationService(UserAreaRepository areas, AttemptRecorder recorder, AttemptLimits limits,
			AreaRemovalGuard removalGuard, VerificationEligibility eligibility, AreaParameters parameters,
			ApplicationEventPublisher events, Clock clock) {
		this.areas = areas;
		this.recorder = recorder;
		this.limits = limits;
		this.removalGuard = removalGuard;
		this.eligibility = eligibility;
		this.parameters = parameters;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * UC-AR-01, or UC-AR-03 when the command names an area to replace. A fix within
	 * {@code area.renew_radius_m} of a live area renews that area instead (UC-AR-01 A4).
	 * @throws ConflictException {@code AREA_LIMIT_REACHED} when the user already has
	 * {@code area.max_count} live areas and replaces none
	 */
	@Transactional
	public VerificationOutcome verify(VerifyAreaCommand command) {
		UUID userId = command.userId();
		eligibility.require(userId);
		Instant now = clock.instant();
		List<UserArea> liveAreas = areas.findLiveAreas(userId);
		Optional<UserArea> nearbyArea = closestRenewable(liveAreas, fixLocationOf(command.submission()));
		if (nearbyArea.isPresent()) {
			return renew(nearbyArea.get(), command.submission(), now);
		}
		AreaPlacement placement = placeNewArea(command, liveAreas, now);
		limits.consumeDailyAttempt(userId);
		Evaluation evaluation = recorder.evaluate(userId, command.submission(), now);
		UUID attemptId = UUID.randomUUID();
		if (!evaluation.isAccepted()) {
			recorder.record(evaluation, new AttemptTarget(attemptId, userId, null, placement.kind(), null));
			return rejected(attemptId, evaluation, OptionalLong.empty());
		}
		placement.replacedArea().ifPresent(replaced -> removeReplaced(replaced, now));
		UserArea area = createArea(command, placement, attemptId, now);
		if (liveAreas.stream().noneMatch(other -> other.isLive() && other.isActive())) {
			area.activate(RangeLevel.DEFAULT, now);
		}
		areas.saveAndFlush(area);
		recorder.record(evaluation, new AttemptTarget(attemptId, userId, area.getId(), placement.kind(), null));
		events.publishEvent(new AreaVerified(userId, area.getId(), area.getExpiresAt(), false));
		return new Verified(AreaView.from(area), false);
	}

	/**
	 * UC-AR-02 for a VERIFIED, EXPIRING or EXPIRED area of the user.
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException
	 * {@code AREA_NOT_FOUND} for an area of another user
	 */
	@Transactional
	public VerificationOutcome renew(UUID userId, UUID areaId, LocationSubmission submission) {
		eligibility.require(userId);
		UserArea area = areas.getOwned(areaId, userId);
		if (!area.isLive()) {
			throw new ConflictException(ErrorCodes.AREA_STATUS_CONFLICT, "A removed area cannot be renewed.");
		}
		return renew(area, submission, clock.instant());
	}

	private VerificationOutcome renew(UserArea area, LocationSubmission submission, Instant now) {
		limits.consumeDailyAttempt(area.getUserId());
		RenewalCheck check = area.checkRenewal(fixLocationOf(submission), parameters.renewalRadius());
		Evaluation evaluation = recorder.evaluate(area.getUserId(), submission, now);
		if (evaluation.isAccepted() && !check.isWithinRadius()) {
			evaluation = evaluation.rejectedAlsoFor(RejectionReason.OUT_OF_RENEW_RADIUS);
		}
		UUID attemptId = UUID.randomUUID();
		AttemptTarget target = new AttemptTarget(attemptId, area.getUserId(), area.getId(), VerificationKind.RENEWAL,
				check.distanceMeters());
		if (!evaluation.isAccepted()) {
			recorder.record(evaluation, target);
			return rejected(attemptId, evaluation,
					check.isWithinRadius() ? OptionalLong.empty() : OptionalLong.of(check.roundedDistanceMeters()));
		}
		area.renew(attemptId, now, parameters.validity());
		areas.saveAndFlush(area);
		recorder.record(evaluation, target);
		events.publishEvent(new AreaVerified(area.getUserId(), area.getId(), area.getExpiresAt(), true));
		return new Verified(AreaView.from(area), true);
	}

	private Optional<UserArea> closestRenewable(List<UserArea> liveAreas, GeoPoint fixLocation) {
		return liveAreas.stream()
			.map(area -> new Candidate(area, area.checkRenewal(fixLocation, parameters.renewalRadius())))
			.filter(candidate -> candidate.check().isWithinRadius())
			.min(Comparator.comparingDouble(candidate -> candidate.check().distanceMeters()))
			.map(Candidate::area);
	}

	private AreaPlacement placeNewArea(VerifyAreaCommand command, List<UserArea> liveAreas, Instant now) {
		if (command.replaceAreaId() != null) {
			UserArea replaced = areas.getOwned(command.replaceAreaId(), command.userId());
			if (!replaced.isLive()) {
				throw new ConflictException(ErrorCodes.AREA_STATUS_CONFLICT, "A removed area cannot be replaced.");
			}
			limits.requireAreaChangeAllowed(command.userId(), now);
			removalGuard.requireRemovable(replaced);
			return new AreaPlacement(replaced.getSlot(), VerificationKind.REPLACEMENT, Optional.of(replaced));
		}
		OptionalInt freeSlot = parameters.areaSlots()
			.firstFree(liveAreas.stream().map(area -> (int) area.getSlot()).toList());
		if (freeSlot.isEmpty()) {
			throw new ConflictException(ErrorCodes.AREA_LIMIT_REACHED,
					"You already have the maximum number of areas; remove or replace one.",
					Map.of("areaIds", liveAreas.stream().map(UserArea::getId).toList()));
		}
		limits.requireAreaChangeAllowed(command.userId(), now);
		return new AreaPlacement(freeSlot.getAsInt(), VerificationKind.INITIAL, Optional.empty());
	}

	private void removeReplaced(UserArea replaced, Instant now) {
		replaced.remove(now);
		areas.saveAndFlush(replaced);
		events.publishEvent(new AreaRemoved(replaced.getUserId(), replaced.getId(), now));
	}

	private UserArea createArea(VerifyAreaCommand command, AreaPlacement placement, UUID attemptId, Instant now) {
		AreaLabel label = command.label() == null ? AreaLabel.defaultFor(placement.slot()) : command.label();
		NewArea newArea = new NewArea(command.userId(), placement.slot(), label,
				parameters.gridSnapper().snap(fixLocationOf(command.submission())), attemptId);
		return UserArea.verified(newArea, now, parameters.validity());
	}

	private static Rejected rejected(UUID attemptId, Evaluation evaluation, OptionalLong roundedDistanceMeters) {
		return new Rejected(attemptId, evaluation.verdict().rejectionReasons(), roundedDistanceMeters);
	}

	private static GeoPoint fixLocationOf(LocationSubmission submission) {
		return submission.reading().fix().location();
	}

	private record Candidate(UserArea area, RenewalCheck check) {
	}

	private record AreaPlacement(int slot, VerificationKind kind, Optional<UserArea> replacedArea) {
	}

}
