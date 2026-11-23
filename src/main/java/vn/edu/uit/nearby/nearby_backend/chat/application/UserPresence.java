package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.util.UUID;

/**
 * Whether a user has a live STOMP session on any replica (ch5 "trạng thái trực tuyến"). A
 * message to an offline recipient becomes a notification (FR-NT-01).
 */
public interface UserPresence {

	boolean isOnline(UUID userId);

}
