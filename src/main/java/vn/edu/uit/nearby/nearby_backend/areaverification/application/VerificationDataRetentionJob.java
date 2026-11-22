package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.AreaVerificationAttemptRepository;

/**
 * UC-SY-11, daily at 02:00 (FR-AR-10, NFR-15): clears precise fixes older than
 * {@code location.retention_days} while keeping the result and grid cell, and deletes
 * attempts older than {@code area.attempt_retention_months} (SRS section 4.5). Running it
 * again changes nothing, so an interrupted run is simply finished by the next one.
 */
@Component
public class VerificationDataRetentionJob {

	private final AreaVerificationAttemptRepository attempts;

	private final AreaParameters parameters;

	private final Clock clock;

	VerificationDataRetentionJob(AreaVerificationAttemptRepository attempts, AreaParameters parameters, Clock clock) {
		this.attempts = attempts;
		this.parameters = parameters;
		this.clock = clock;
	}

	@Transactional
	@Scheduled(cron = AreaJobSchedules.DAILY_AT_02_00, zone = AreaJobSchedules.VIETNAM_ZONE)
	public void run() {
		runAt(clock.instant());
	}

	@Transactional
	public RetentionResult runAt(Instant now) {
		int purged = attempts.purgeLocationsCreatedBefore(now.minus(parameters.preciseLocationRetention()), now);
		Instant attemptCutoff = now.atOffset(ZoneOffset.UTC).minus(parameters.attemptRetention()).toInstant();
		int deleted = attempts.deleteCreatedBefore(attemptCutoff);
		return new RetentionResult(purged, deleted);
	}

	public record RetentionResult(int locationsPurged, int attemptsDeleted) {
	}

}
