package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * What a list screen needs to show another user (A17, A18): never a phone number or
 * email.
 *
 * @param displayName "Người dùng đã xoá" for a deleted account
 * @param avatarUrl short-lived download URL, null when the user has no avatar
 */
public record UserSummary(UUID id, String displayName, @Nullable String avatarUrl, boolean deleted) {

}
