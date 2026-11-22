package vn.edu.uit.nearby.nearby_backend.areaverification.web;

import java.util.EnumMap;
import java.util.Map;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.RejectionReason;

/**
 * The Vietnamese guidance FR-AR-05 requires with every rejection reason, telling the user
 * how to get a fix that passes.
 */
final class RejectionGuidance {

	private static final Map<RejectionReason, String> MESSAGES = new EnumMap<>(Map.of(RejectionReason.MOCK_LOCATION,
			"Hãy tắt ứng dụng hoặc chế độ vị trí giả rồi thử lại.", RejectionReason.LOW_ACCURACY,
			"Tín hiệu GPS chưa đủ chính xác. Hãy ra chỗ thoáng hoặc ngoài trời rồi thử lại.", RejectionReason.STALE_FIX,
			"Vị trí đã cũ. Hãy lấy lại vị trí hiện tại rồi thử lại.", RejectionReason.IMPOSSIBLE_SPEED,
			"Vị trí thay đổi quá nhanh so với lần xác minh trước. Hãy thử lại tại nơi bạn đang có mặt.",
			RejectionReason.OUT_OF_RENEW_RADIUS,
			"Bạn đang ở quá xa khu vực này. Hãy thêm khu vực mới hoặc thay thế khu vực này."));

	private RejectionGuidance() {
	}

	static String messageFor(RejectionReason reason) {
		return MESSAGES.get(reason);
	}

}
