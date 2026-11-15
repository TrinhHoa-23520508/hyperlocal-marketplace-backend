package vn.edu.uit.nearby.nearby_backend.identity.infrastructure.mail;

final class AccountEmailTemplates {

	static final String VERIFICATION_SUBJECT = "Xác minh email tài khoản Nearby";

	static final String PASSWORD_RESET_SUBJECT = "Đặt lại mật khẩu Nearby";

	private static final String VERIFICATION_BODY = """
			Xin chào %s,

			Bạn vừa thêm địa chỉ email này vào tài khoản Nearby. Mở liên kết dưới đây để xác minh email:
			%s

			Nếu bạn không thực hiện thao tác này, hãy bỏ qua email này.
			""";

	private static final String PASSWORD_RESET_BODY = """
			Xin chào %s,

			Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản Nearby của bạn. Mở liên kết dưới đây để đặt mật khẩu mới:
			%s

			Nếu bạn không yêu cầu, hãy bỏ qua email này; mật khẩu hiện tại vẫn được giữ nguyên.
			""";

	private AccountEmailTemplates() {
	}

	static String verificationBody(String displayName, String link) {
		return VERIFICATION_BODY.formatted(displayName, link);
	}

	static String passwordResetBody(String displayName, String link) {
		return PASSWORD_RESET_BODY.formatted(displayName, link);
	}

}
