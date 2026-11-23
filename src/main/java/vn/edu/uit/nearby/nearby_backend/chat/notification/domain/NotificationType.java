package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

import static vn.edu.uit.nearby.nearby_backend.chat.notification.domain.DeepLink.Screen.ACCOUNT;
import static vn.edu.uit.nearby.nearby_backend.chat.notification.domain.DeepLink.Screen.AREAS;
import static vn.edu.uit.nearby.nearby_backend.chat.notification.domain.DeepLink.Screen.CONVERSATIONS;
import static vn.edu.uit.nearby.nearby_backend.chat.notification.domain.DeepLink.Screen.DISPUTES;
import static vn.edu.uit.nearby.nearby_backend.chat.notification.domain.DeepLink.Screen.HOME;
import static vn.edu.uit.nearby.nearby_backend.chat.notification.domain.DeepLink.Screen.TRANSACTIONS;
import static vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationCategory.CHAT;
import static vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationCategory.SYSTEM;
import static vn.edu.uit.nearby.nearby_backend.chat.notification.domain.NotificationCategory.TRADE;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.chat.notification.domain.DeepLink.Screen;

/**
 * The catalogue of in-app notifications (FR-NT-01): for each type its category, the
 * screen it opens and the Vietnamese title and body, with {@code {name}} placeholders. A
 * type marked with a register row is reserved: its producer module is not built yet and
 * nothing creates it until that module adds its listener.
 */
public enum NotificationType {

	// Producers that exist now
	MESSAGE_RECEIVED(CHAT, CONVERSATIONS, "{sender}", "{preview}"),
	WELCOME(SYSTEM, HOME, "Chào mừng đến với Nearby",
			"Xác minh khu vực của bạn để bắt đầu mua bán với hàng xóm gần nhà."),
	AREA_EXPIRING(SYSTEM, AREAS, "Khu vực sắp hết hạn",
			"Khu vực của bạn sẽ hết hạn vào {expiresOn}. Hãy gia hạn để tiếp tục giao dịch."),
	AREA_EXPIRED(SYSTEM, AREAS, "Khu vực đã hết hạn",
			"Khu vực của bạn đã hết hạn. Hãy xác minh lại để tiếp tục giao dịch."),
	SANCTION_SUSPENDED(SYSTEM, ACCOUNT, "Tài khoản bị tạm khoá", "Tài khoản của bạn bị tạm khoá đến {until}."),
	SANCTION_BANNED(SYSTEM, ACCOUNT, "Tài khoản bị cấm",
			"Tài khoản của bạn đã bị cấm. Hãy liên hệ hỗ trợ nếu bạn cho rằng đây là nhầm lẫn."),
	SANCTION_LIFTED(SYSTEM, ACCOUNT, "Tài khoản đã được khôi phục", "Bạn có thể sử dụng Nearby trở lại."),

	// NT-W1: Transaction
	OFFER_RECEIVED(TRADE, TRANSACTIONS, "Có đề nghị mới", "{buyer} đề nghị {price} cho \"{listing}\"."),
	OFFER_ACCEPTED(TRADE, TRANSACTIONS, "Đề nghị được chấp nhận", "{seller} đã chấp nhận đề nghị cho \"{listing}\"."),
	OFFER_REJECTED(TRADE, TRANSACTIONS, "Đề nghị bị từ chối", "{seller} đã từ chối đề nghị cho \"{listing}\"."),
	OFFER_WITHDRAWN(TRADE, TRANSACTIONS, "Đề nghị đã được rút", "{buyer} đã rút đề nghị cho \"{listing}\"."),
	OFFER_EXPIRED(TRADE, TRANSACTIONS, "Đề nghị hết hạn", "Đề nghị cho \"{listing}\" đã hết hạn."),
	SALE_MARKED(TRADE, TRANSACTIONS, "Người bán đánh dấu đã bán cho bạn",
			"{seller} đánh dấu \"{listing}\" đã bán cho bạn. Hãy xác nhận hoặc từ chối."),
	APPOINTMENT_CONFIRMED(TRADE, TRANSACTIONS, "Lịch hẹn đã xác nhận", "Hẹn gặp cho \"{listing}\" lúc {time}."),
	APPOINTMENT_REMINDER(TRADE, TRANSACTIONS, "Sắp đến giờ hẹn", "Bạn có hẹn cho \"{listing}\" lúc {time}."),
	HANDOVER_CONFIRMED(TRADE, TRANSACTIONS, "Đã giao hàng", "{seller} xác nhận đã giao \"{listing}\"."),
	RECEIPT_CONFIRMED(TRADE, TRANSACTIONS, "Đã nhận hàng", "{buyer} xác nhận đã nhận \"{listing}\"."),
	TRANSACTION_COMPLETED(TRADE, TRANSACTIONS, "Giao dịch hoàn tất", "Giao dịch \"{listing}\" đã hoàn tất."),

	// NT-W2: Payment
	PAYMENT_SUCCEEDED(TRADE, TRANSACTIONS, "Thanh toán thành công",
			"Khoản thanh toán {amount} cho \"{listing}\" đã được giữ hộ."),
	PAYMENT_REFUNDED(TRADE, TRANSACTIONS, "Đã hoàn tiền", "Bạn được hoàn {amount} cho \"{listing}\"."),
	PAYOUT_SENT(TRADE, TRANSACTIONS, "Đã chi trả", "Bạn nhận {amount} cho \"{listing}\"."),

	// NT-W3: Reputation
	REVIEW_INVITE(TRADE, TRANSACTIONS, "Mời đánh giá", "Hãy đánh giá giao dịch \"{listing}\" với {partner}."),
	REVIEW_PUBLISHED(TRADE, TRANSACTIONS, "Đánh giá đã được công bố", "{partner} đã đánh giá giao dịch \"{listing}\"."),

	// NT-W4: Moderation (the target screen is fixed there)
	REPORT_RESULT(SYSTEM, HOME, "Kết quả báo cáo", "Báo cáo của bạn đã được xử lý: {outcome}"),
	DISPUTE_UPDATE(SYSTEM, DISPUTES, "Cập nhật tranh chấp", "Tranh chấp về \"{listing}\" có cập nhật: {outcome}"),
	APPEAL_RESULT(SYSTEM, ACCOUNT, "Kết quả kháng nghị", "Kháng nghị của bạn đã có kết quả: {outcome}");

	static final int MAX_TITLE_CHARS = 120;

	static final int MAX_BODY_CHARS = 300;

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

	private static final String ELLIPSIS = "…";

	private final NotificationCategory category;

	private final Screen screen;

	private final String titleTemplate;

	private final String bodyTemplate;

	NotificationType(NotificationCategory category, Screen screen, String titleTemplate, String bodyTemplate) {
		this.category = category;
		this.screen = screen;
		this.titleTemplate = titleTemplate;
		this.bodyTemplate = bodyTemplate;
	}

	public NotificationCategory category() {
		return category;
	}

	public Screen screen() {
		return screen;
	}

	/**
	 * @param targetId what the deep link opens; null for a screen without an id
	 * @param arguments values of the placeholders of the title and body
	 * @throws IllegalArgumentException when a placeholder has no value or the target id
	 * does not fit the screen: a bug in the producer, not a user error
	 */
	public NotificationContent render(@Nullable String targetId, Map<String, String> arguments) {
		DeepLink deepLink = new DeepLink(screen, screen.needsId() ? targetId : null);
		return new NotificationContent(fit(fill(titleTemplate, arguments), MAX_TITLE_CHARS),
				fit(fill(bodyTemplate, arguments), MAX_BODY_CHARS), deepLink);
	}

	private static String fill(String template, Map<String, String> arguments) {
		return PLACEHOLDER.matcher(template).replaceAll(match -> {
			String value = arguments.get(match.group(1));
			if (value == null) {
				throw new IllegalArgumentException("Missing notification argument: " + match.group(1));
			}
			return Matcher.quoteReplacement(value);
		});
	}

	private static String fit(String text, int maxChars) {
		if (text.codePointCount(0, text.length()) <= maxChars) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, maxChars - 1)) + ELLIPSIS;
	}

}
