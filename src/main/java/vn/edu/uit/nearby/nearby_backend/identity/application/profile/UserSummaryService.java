package vn.edu.uit.nearby.nearby_backend.identity.application.profile;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.UserSummaries;
import vn.edu.uit.nearby.nearby_backend.identity.UserSummary;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;

@Service
public class UserSummaryService implements UserSummaries {

	private final UserRepository users;

	private final ImageStorage storage;

	public UserSummaryService(UserRepository users, ImageStorage storage) {
		this.users = users;
		this.storage = storage;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, UserSummary> summariesOf(Collection<UUID> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, this::summaryOf));
	}

	private UserSummary summaryOf(User user) {
		String avatarKey = user.getAvatarKey();
		String avatarUrl = avatarKey == null ? null : storage.presignDownload(avatarKey).url();
		return new UserSummary(user.getId(), user.getDisplayName(), avatarUrl, user.isDeleted());
	}

}
