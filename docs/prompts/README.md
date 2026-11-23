# Vibe code frontend cho Nearby

Hướng dẫn dùng AI (Claude Code, Cursor, Copilot...) để viết app Android cho người dùng và web admin dựa trên backend này. Mỗi file prompt trong thư mục là "đề bài" đã viết sẵn. Bạn chỉ cần kèm đúng context và làm theo quy trình bên dưới.

| Ứng dụng | Prompt | Luồng nghiệp vụ dùng |
|---|---|---|
| Android cho người dùng (Kotlin, Compose) | [`android-user-app.md`](android-user-app.md) | F1–F12 và F15 (cổng giao dịch) trong [`../api/identity-api.md`](../api/identity-api.md#business-flows-per-screen); FA1–FA5 (khu vực, A27/A28) trong [`../api/area-api.md`](../api/area-api.md#business-flows-per-screen); FL1–FL9 (tin đăng, bảng tin, tìm kiếm, bản đồ, chi tiết, yêu thích; A06–A15) trong [`../api/listing-api.md`](../api/listing-api.md#business-flows-per-screen) |
| Web admin (React, Vite, TypeScript) | [`admin-web.md`](admin-web.md) | F13–F14 |

SRS không có web cho người dùng: người dùng chỉ dùng app Android. Đừng vibe code web user.

## 1. Context phải đưa vào mỗi phiên

AI chỉ làm đúng khi có đủ thông tin. Đưa **đủ** những thứ trong bảng, theo thứ tự ưu tiên. Mục nào thiếu thì AI sẽ tự bịa ra.

| # | Context | Lấy ở đâu | Vì sao cần |
|---|---|---|---|
| 1 | File prompt của ứng dụng | `docs/prompts/android-user-app.md` hoặc `admin-web.md` | Vai trò, stack, phạm vi, quy tắc, tiêu chí nghiệm thu |
| 2 | Luồng nghiệp vụ của tính năng đang làm | Mục *Business flows per screen* trong `docs/api/identity-api.md`, `docs/api/area-api.md` hoặc `docs/api/listing-api.md` (chỉ chép các luồng F/FA/FL liên quan) | Màn hình nào gọi API nào, theo thứ tự nào, xử lý từng mã lỗi ra sao |
| 3 | OpenAPI | `docs/api/openapi.yml` | Kiểu dữ liệu chính xác để sinh code gọi API và model; mọi mã lỗi theo endpoint |
| 4 | Chi tiết endpoint | Các mục chi tiết endpoint trong `docs/api/identity-api.md`, `docs/api/area-api.md` hoặc `docs/api/listing-api.md` | Ràng buộc field, tác dụng phụ, máy trạng thái |
| 5 | Mô tả màn hình trong SRS | `docs-local/SRS_Hyperlocal_Marketplace.pdf`: Bảng 61 (A01–A37, W01–W16) và mục FR của tính năng (3.1 cho định danh) | Nội dung và mục đích từng màn hình; bản prompt chỉ tóm tắt |
| 6 | Quy tắc code | Android: `.claude/clean-code-skill/references/06-android-kotlin.md` và `01-clean-code-principles.md`. Web: phần *Tech stack* và *Project structure* trong `admin-web.md` | Giữ cùng kiến trúc và chuẩn test với backend |
| 7 | Design | Figma, bộ màu, logo, font (nếu có). Không có thì dùng phần *Design direction* trong prompt | Giao diện thống nhất |
| 8 | Code frontend hiện có | Repo frontend (AI tự đọc nếu chạy trong repo đó) | Nối tiếp, không viết lại thứ đã có |
| 9 | Môi trường chạy thử | Mục 3 bên dưới | Gọi được backend thật để kiểm tra |

**Không đưa vào**: file `.env` thật, mật khẩu, API key eSMS/Gmail, token thật, toàn bộ PDF SRS cho một tính năng nhỏ (chỉ trích phần liên quan).

Mẫu tin nhắn mở đầu một phiên:

```text
Đọc docs/prompts/android-user-app.md (đề bài) và .claude/clean-code-skill/references/06-android-kotlin.md (quy tắc).
Tính năng lần này: Đăng ký + Đăng nhập (luồng F1, F2, F3, F4 trong docs/api/identity-api.md).
Kiểu dữ liệu lấy từ docs/api/openapi.yml. Mô tả màn hình A02, A03, A04 ở SRS Bảng 61 (đính kèm đoạn trích).
Lên kế hoạch trước (màn hình, ViewModel, UseCase, API, test), chờ tôi duyệt rồi mới code.
```

## 2. Quy trình cho mỗi tính năng

1. **Một tính năng một phiên**: chọn một nhóm luồng (ví dụ F2–F4). Không giao cả app trong một lần.
2. **Bắt lên kế hoạch trước**: danh sách màn hình, state, use case, endpoint, cách xử lý từng mã lỗi, danh sách test. Đối chiếu với bảng luồng rồi mới cho code.
3. **Tầng gọi API trước, giao diện sau**:
   - Web: sinh type bằng `npx openapi-typescript docs/api/openapi.yml -o src/api/schema.ts`.
   - Android: viết Retrofit interface và model theo `openapi.yml`. Có thể dùng `openapi-generator` (generator `kotlin`, library `jvm-retrofit2`) làm tham chiếu, nhưng giữ model trong tầng `data`.
4. **Test với server giả** (MockWebServer hoặc MSW) cho từng mã lỗi trong bảng luồng, sau đó mới chạy với backend thật.
5. **Chạy thử theo bảng luồng**: mỗi dòng trong bảng là một ca thử. Bảng có cột lỗi thì phải thử cho ra đúng lỗi đó.
6. **Review** như review code của người: kiến trúc theo quy tắc, không log token, không tự đặt lại header `Content-Type` khi upload ảnh, refresh token chỉ chạy một luồng, chuỗi tiếng Việt có dấu.

Khi backend đổi API: sinh lại `openapi.yml` (xem skill `references/08-api-contract.md`), đưa diff cho AI và yêu cầu cập nhật model, xử lý lỗi và test.

## 3. Chạy backend để thử

```sh
docker compose up -d          # Postgres, Redis, MinIO
./gradlew bootRun             # API ở http://localhost:8080, Swagger UI /swagger-ui.html
```

| Việc | Cách làm |
|---|---|
| Lấy mã OTP khi chưa có SMS thật | Đặt `SMS_PROVIDER=log` trong `.env`, mã in ra log backend (tìm `[sms disabled]`) |
| Lấy link xác minh email hoặc đặt lại mật khẩu | `MAIL_ENABLED=false`, link in ra log backend |
| Tài khoản admin | `ADMIN_EMAIL` / `ADMIN_PASSWORD` trong `.env` (tạo khi khởi động) |
| Android emulator gọi backend | Base URL `http://10.0.2.2:8080`; ảnh: `MINIO_PUBLIC_ENDPOINT=http://10.0.2.2:9000` |
| Điện thoại thật | Base URL `http://<IP-LAN>:8080`; `MINIO_PUBLIC_ENDPOINT=http://<IP-LAN>:9000`; cho phép cleartext HTTP trong bản debug |
| Web admin | Chạy Vite ở cổng 5173 (đã nằm trong `CORS_ALLOWED_ORIGINS`) |
| Gọi thử bằng tay | Import `docs/postman/nearby-identity.postman_collection.json`, rồi `nearby-area` và `nearby-listing` |
| Xem metric khi thử tải | `docker compose --profile monitoring up -d prometheus grafana`, Grafana http://localhost:3000 |

## 4. Lỗi hay gặp khi vibe code

| Triệu chứng | Nguyên nhân | Nhắc AI |
|---|---|---|
| Upload ảnh trả `SignatureDoesNotMatch` | Gửi `Content-Type` khác `requiredHeaders`, thêm `charset`, hoặc sửa URL | Dùng nguyên `uploadUrl` và header backend trả về; body là bytes thô |
| Thỉnh thoảng bị văng ra màn hình đăng nhập | Hai lần refresh chạy song song, backend coi là token bị đánh cắp | Chỉ cho một lần refresh chạy (Mutex hoặc single-flight), các request khác xếp hàng chờ |
| Báo lỗi sai ngữ cảnh | Rẽ nhánh theo `detail` hoặc HTTP status | Rẽ nhánh theo `code` của problem detail |
| Web báo lỗi CORS | Origin không nằm trong danh sách cho phép | Chạy ở `http://localhost:5173` hoặc thêm origin vào `CORS_ALLOWED_ORIGINS` |
| Ảnh đại diện vỡ sau 10 phút | Lưu `avatarUrl` đã ký lâu dài | Gọi lại `GET /users/me` để lấy URL mới |
