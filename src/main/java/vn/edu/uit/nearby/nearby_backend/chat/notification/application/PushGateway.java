package vn.edu.uit.nearby.nearby_backend.chat.notification.application;

/**
 * Sends one push to one device (FR-NT-02). The in-app notification is the source of
 * truth, so an implementation never throws for an FCM problem: it answers
 * {@link PushResult#FAILED} and the business flow carries on.
 */
public interface PushGateway {

	PushResult send(String deviceToken, PushMessage message);

	/**
	 * @param deepLink what the app opens when the user taps the push
	 */
	record PushMessage(String title, String body, String deepLink) {
	}

	enum PushResult {

		SENT,

		/**
		 * FCM does not know the token any more (app uninstalled, token rotated): delete
		 * it.
		 */
		INVALID_TOKEN,

		/**
		 * Push is switched off ({@code app.push.fcm.credentials} is empty); nothing was
		 * sent and the notification shows SKIPPED.
		 */
		DISABLED,

		/**
		 * FCM was unreachable, slow or refused for another reason; log it and move on.
		 */
		FAILED

	}

}
