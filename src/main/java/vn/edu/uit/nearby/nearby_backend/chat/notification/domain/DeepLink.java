package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

import org.jspecify.annotations.Nullable;

/**
 * An in-app address in the dictionary's form {@code app://<screen>/<id>} (FR-NT-01, plan
 * reading R7). A screen without an id, such as the home tab, is {@code app://home}.
 */
public record DeepLink(Screen screen, @Nullable String targetId) {

	public enum Screen {

		CONVERSATIONS("conversations", true), AREAS("areas", true), TRANSACTIONS("transactions", true),
		LISTINGS("listings", true), USERS("users", true), DISPUTES("disputes", true), HOME("home", false),
		ACCOUNT("account", false);

		private final String path;

		private final boolean needsId;

		Screen(String path, boolean needsId) {
			this.path = path;
			this.needsId = needsId;
		}

		public boolean needsId() {
			return needsId;
		}

		String path() {
			return path;
		}

	}

	public DeepLink {
		boolean hasId = targetId != null && !targetId.isBlank();
		if (screen.needsId() != hasId) {
			throw new IllegalArgumentException(
					"Screen " + screen + (screen.needsId() ? " needs a target id." : " takes no target id."));
		}
	}

	public String asUri() {
		return "app://" + screen.path() + (targetId == null ? "" : "/" + targetId);
	}

}
