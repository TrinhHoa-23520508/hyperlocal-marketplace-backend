package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.time.Duration;

/**
 * Hard-rule limits of FR-AR-05: {@code area.accuracy_threshold_m},
 * {@code area.max_fix_age_s} and {@code area.max_speed_kmh}.
 */
public record AntiSpoofingThresholds(double maxAccuracyMeters, Duration maxFixAge, double maxSpeedKmh) {
}
