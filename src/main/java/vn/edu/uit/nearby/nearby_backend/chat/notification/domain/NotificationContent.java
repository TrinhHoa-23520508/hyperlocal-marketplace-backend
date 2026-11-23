package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

/**
 * What one notification shows: the title and body fit the columns (120 and 300
 * characters) and the deep link opens the right screen (FR-NT-01).
 */
public record NotificationContent(String title, String body, DeepLink deepLink) {

}
