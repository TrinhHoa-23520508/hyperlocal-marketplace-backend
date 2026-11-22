package vn.edu.uit.nearby.nearby_backend.identity.application.profile;

import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

public interface ProfileService {

	UserProfile getProfile(UUID userId);

	/**
	 * @throws UnprocessableRequestException {@code NAME_CHANGE_TOO_SOON} when the display
	 * name changed less than {@code profile.display_name_change_days} ago (FR-ID-11)
	 */
	UserProfile updateProfile(UUID userId, UpdateProfileCommand command);

	/**
	 * What anyone may see about a user (FR-ID-13): never coordinates or contact data. A
	 * deleted account shows only the anonymised name.
	 */
	PublicProfile getPublicProfile(UUID userId);

	/**
	 * Step 1 of an avatar change: returns a presigned URL to upload the image directly to
	 * storage.
	 * @throws InvalidRequestException {@code INVALID_IMAGE} for a format other than JPEG,
	 * PNG or WebP, or a size over the limit
	 */
	AvatarUpload createAvatarUpload(UUID userId, AvatarUploadCommand command);

	/**
	 * Step 2: checks the uploaded object and makes it the avatar. An invalid upload is
	 * deleted.
	 * @throws InvalidRequestException {@code INVALID_AVATAR_KEY},
	 * {@code AVATAR_NOT_UPLOADED} or {@code INVALID_IMAGE}
	 */
	UserProfile confirmAvatar(UUID userId, String objectKey);

	void removeAvatar(UUID userId);

}
