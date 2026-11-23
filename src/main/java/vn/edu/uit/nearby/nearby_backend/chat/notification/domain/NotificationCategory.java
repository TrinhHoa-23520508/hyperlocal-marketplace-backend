package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

/**
 * {@code notifications.category}, the unit of the user's push switches (FR-NT-03). The
 * assignment of types to categories is plan reading R5.
 */
public enum NotificationCategory {

	CHAT, TRADE,

	/**
	 * From R2 (keyword alerts); the column and the switch exist already.
	 */
	KEYWORD_ALERT,

	/**
	 * Needs the PUSH_MARKETING consent; no producer in R1.
	 */
	MARKETING,

	/**
	 * Cannot be switched off (FR-NT-03).
	 */
	SYSTEM

}
