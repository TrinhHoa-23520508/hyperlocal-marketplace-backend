package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Instant;
import java.util.OptionalDouble;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsFix;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.AreaVerificationAttempt;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.AttemptEvidence;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.AttemptSubject;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.VerificationKind;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.AreaVerificationPolicy;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.RejectionReason;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.Verdict;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.VerificationContext;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.AreaVerificationAttemptRepository;

/**
 * Runs the anti-spoofing checks against the user's previous accepted fix (FR-AR-05) and
 * stores every attempt with its result, reasons and device signals (FR-AR-10).
 */
@Component
class AttemptRecorder {

	private final AreaVerificationAttemptRepository attempts;

	private final AreaVerificationPolicy policy;

	private final AreaParameters parameters;

	AttemptRecorder(AreaVerificationAttemptRepository attempts, AreaVerificationPolicy policy,
			AreaParameters parameters) {
		this.attempts = attempts;
		this.policy = policy;
		this.parameters = parameters;
	}

	Evaluation evaluate(UUID userId, LocationSubmission submission, Instant now) {
		GpsFix previousAcceptedFix = attempts.findLatestAcceptedWithFix(userId)
			.flatMap(AreaVerificationAttempt::preciseFix)
			.orElse(null);
		VerificationContext context = new VerificationContext(submission.reading(), submission.device(),
				previousAcceptedFix, now, parameters.antiSpoofingThresholds());
		return new Evaluation(submission, policy.evaluate(context), context.impliedSpeedKmh(), now);
	}

	void record(Evaluation evaluation, AttemptTarget target) {
		LocationSubmission submission = evaluation.submission();
		AttemptSubject subject = new AttemptSubject(target.attemptId(), target.userId(), target.areaId(), target.kind(),
				submission.idempotencyKey(), submission.appVersion());
		AttemptEvidence evidence = new AttemptEvidence(submission.reading(),
				parameters.gridSnapper().snap(submission.reading().fix().location()), evaluation.impliedSpeedKmh(),
				target.distanceMeters(), evaluation.receivedAt());
		attempts.save(AreaVerificationAttempt.record(subject, evidence, evaluation.verdict()));
	}

	/**
	 * The checked attempt, before it is stored.
	 */
	record Evaluation(LocationSubmission submission, Verdict verdict, OptionalDouble impliedSpeedKmh,
			Instant receivedAt) {

		boolean isAccepted() {
			return verdict.isAccepted();
		}

		Evaluation rejectedAlsoFor(RejectionReason reason) {
			return new Evaluation(submission, verdict.rejectedAlsoFor(reason), impliedSpeedKmh, receivedAt);
		}

	}

	/**
	 * Which attempt row to write and what it is about.
	 *
	 * @param areaId the area created or renewed; null when no area was created
	 * @param distanceMeters distance to the area center, for renewals
	 */
	record AttemptTarget(UUID attemptId, UUID userId, @Nullable UUID areaId, VerificationKind kind,
			@Nullable Double distanceMeters) {
	}

}
