package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.UUID;

/**
 * A device logged out (FR-ID-05); Notify stops pushing to its FCM token.
 */
public record DeviceSignedOut(UUID userId, String fcmToken) {

}
