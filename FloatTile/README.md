# FloatTile

FloatTile thêm ô **“Claude nổi”** vào bảng cài đặt nhanh (bảng kéo từ trên màn hình xuống). Bấm ô đó, ứng dụng Claude sẽ mở thành một cửa sổ nhỏ nổi ở giữa màn hình, nằm trên ứng dụng bạn đang dùng.

Ứng dụng viết bằng Kotlin, chạy từ Android 8.0 (minSdk 26). Nó được viết với máy thử là Xiaomi POCO C65 chạy HyperOS.

---

## 1. Tải file APK

APK (file cài đặt ứng dụng Android) được GitHub tự build mỗi khi mã trong thư mục `FloatTile/` thay đổi.

- **Cách dễ nhất, tải thẳng trên điện thoại:** mở trang kho GitHub, vào **Releases** (Bản phát hành), chọn **floattile-latest** rồi bấm **FloatTile.apk**.
- **Cách khác:** vào tab **Actions**, chọn lần chạy mới nhất của “Build FloatTile APK”, kéo xuống mục **Artifacts** và tải **FloatTile-apk**. File tải về là file .zip, cần giải nén để lấy file .apk. Cách này phải đăng nhập GitHub.

## 2. Cài APK lên máy (HyperOS)

1. Mở file `FloatTile.apk` vừa tải, từ thông báo tải xuống hoặc từ ứng dụng **Quản lý tệp**.
2. Nếu máy hỏi “Cho phép cài ứng dụng từ nguồn này?”, bật **Cho phép** cho trình duyệt hoặc trình quản lý tệp bạn đang dùng.
3. HyperOS có thể hiện màn hình cảnh báo và quét an toàn. Chờ quét xong, tích “Tôi hiểu rủi ro” (nếu có) rồi bấm **Cài đặt**.
4. Nếu bấm ô mà không có gì xảy ra, vào **Cài đặt → Ứng dụng → Quản lý ứng dụng → FloatTile → Quyền khác**. Bật **“Hiển thị cửa sổ bật lên khi đang chạy trong nền”** và **“Hiển thị cửa sổ bật lên”**. HyperOS có thể chặn việc mở màn hình nếu thiếu hai quyền này.

> Các bản build sau dùng chung một khóa ký (chữ ký số của ứng dụng), nên bạn cài đè trực tiếp được mà không cần gỡ bản cũ.

## 3. Thêm ô vào bảng cài đặt nhanh

**Cách 1:** mở FloatTile và bấm **“Thêm ô ngay”**. Cách này dùng được từ Android 13, nhưng một số bản HyperOS không hỗ trợ.

**Cách 2 (thủ công):**
1. Vuốt từ **góc trên bên phải** xuống để mở Trung tâm điều khiển.
2. Bấm biểu tượng **chỉnh sửa** (hình bút chì hoặc ô vuông, thường ở góc trên).
3. Tìm ô **“Claude nổi”** (biểu tượng khung màn hình có ô nhỏ ở giữa).
4. Nhấn giữ ô và kéo vào vùng các ô đang dùng, rồi bấm **Xong**.

Bấm ô để mở Claude dạng nổi. **Nhấn giữ** ô để mở màn hình cài đặt của FloatTile.

## 4. Lần chạy đầu tiên

1. Mở FloatTile và xem thẻ **Ứng dụng đích**. Thẻ này phải hiện Claude kèm tên gói (tên định danh của ứng dụng, ví dụ `com.anthropic.claude`). Nếu không thấy, bấm **Chọn ứng dụng khác**. Danh sách ghi tên gói của mọi ứng dụng, và ứng dụng có chữ “claude” được đưa lên đầu.
2. Bấm **“Kiểm tra cửa sổ tự do của máy”**. FloatTile sẽ mở một cửa sổ thử nghiệm bằng từng cách, đo xem nó có thật sự nổi không, rồi in kết luận.
3. Nếu cách (a) không được, làm theo **“Hướng dẫn và cấp quyền Shizuku”** để dùng cách (b).

### Kiểm tra tên gói thật của Claude

Tên gói mặc định là `com.anthropic.claude`. Bạn có thể kiểm tra theo một trong các cách sau:
- Trong FloatTile, màn hình chọn ứng dụng ghi tên gói dưới tên mỗi ứng dụng.
- Trong màn hình Shizuku, bấm **“Tìm tên gói Claude (pm list packages)”**. Nút này chạy lệnh `pm list packages | grep -i claude` ngay trên máy.
- Từ máy tính có ADB (công cụ gỡ lỗi Android): `adb shell pm list packages | grep -i claude`.

## 5. Cách FloatTile mở cửa sổ nổi

| Bước | Làm gì | Ghi chú |
|---|---|---|
| Ô cài đặt nhanh | `TileService`. Trên Android 14 trở lên dùng `startActivityAndCollapse(PendingIntent)` | Ô này mở một màn hình trong suốt của FloatTile (màn hình trung chuyển), màn hình đó mới mở Claude |
| Khung cửa sổ | `ActivityOptions.setLaunchBounds(Rect)`: 80% chiều rộng × 60% chiều cao, căn giữa | Chỉnh được trong cài đặt |
| Cờ | `FLAG_ACTIVITY_NEW_TASK \| FLAG_ACTIVITY_MULTIPLE_TASK` | |
| Cách (a) | Gọi hàm ẩn `setLaunchWindowingMode(5)` bằng reflection (gọi hàm theo tên lúc chạy) | Android 8.x dùng `setLaunchStackId(2)` |
| Cách (b) | Qua Shizuku: `am start --windowingMode 5 -f 0x18000000 -n <ứng dụng>`, sau đó `am task resize <mã task> <trái> <trên> <phải> <dưới>` | Xem mục 6 về `--bounds` |
| Kiểm tra | Mở cửa sổ thử nghiệm của chính FloatTile, đọc chế độ cửa sổ của nó (`mWindowingMode=freeform`) | Kết quả được lưu lại để lần bấm sau không phải thử lại |

Ở chế độ **Tự động**, lần bấm ô đầu tiên FloatTile tự chạy kiểm tra nhanh, nên bạn sẽ thấy cửa sổ thử nghiệm hiện lên trong chốc lát. Các lần sau, FloatTile dùng ngay cách đã chạy được. Nếu không cách nào được, FloatTile hiện hộp thoại nói rõ lý do, thay vì im lặng.

## 6. Những chỗ hệ điều hành có thể chặn, và cách thay thế

1. **`am start` không có tùy chọn `--bounds`.** Lệnh `am start` của Android chỉ có `--windowingMode`, không có cách nào đặt kích thước ngay lúc mở. Vì vậy FloatTile mở trước, sau đó đọc mã task (mã của nhóm màn hình vừa mở) bằng `dumpsys activity activities`, rồi đặt kích thước bằng `am task resize`. Nếu bước đặt kích thước lỗi, cửa sổ vẫn nổi nhưng dùng kích thước mặc định của hệ thống, và FloatTile sẽ báo cho bạn.
2. **Hạn chế API ẩn (từ Android 9).** Google không cho ứng dụng thường gọi các hàm nội bộ nằm trong danh sách cấm. Nếu `setLaunchWindowingMode` bị chặn, hàm sẽ coi như không tồn tại. Khi đó FloatTile báo *“Android không cho gọi hàm ẩn…”* và đề xuất dùng Shizuku. FloatTile không dùng thủ thuật nào để vượt qua giới hạn này.
3. **Gọi được hàm nhưng hệ thống phớt lờ.** Một số bản HyperOS, nhất là máy cấu hình thấp, tự quản lý cửa sổ nổi theo cách riêng và có thể bỏ qua yêu cầu từ ứng dụng khác. Màn hình kiểm tra sẽ chỉ ra trường hợp này.
4. **Tùy chọn “Bật cửa sổ dạng tự do” chỉ có hiệu lực sau khi khởi động lại máy.** Hãy khởi động lại sau khi bật. Bạn cũng nên bật **“Buộc các hoạt động có thể thay đổi kích thước”**, vì nếu Claude không cho đổi kích thước, hệ thống sẽ mở nó toàn màn hình. Nếu Shizuku đã có quyền, màn hình kiểm tra có nút bật cả hai tùy chọn này giúp bạn.
5. **Claude đang mở sẵn.** Nếu Claude đã mở toàn màn hình, hệ thống có thể chỉ đưa cửa sổ cũ lên trước. Hãy đóng Claude trong Đa nhiệm rồi bấm ô lại.
6. **Shizuku tắt sau khi khởi động lại máy** (nếu máy không root). Mở Shizuku và bấm Khởi động lại.
7. **Shizuku báo “quyền adb bị giới hạn” (máy Xiaomi/HyperOS).** Đây là cảnh báo của Shizuku: Xiaomi cắt bớt quyền của ADB cho tới khi bạn bật một tùy chọn riêng. Cách gỡ:
   1. Vào **Tùy chọn nhà phát triển**, bật **“Gỡ lỗi USB (Cài đặt bảo mật)”**. Thường cần đăng nhập Tài khoản Xiaomi, lắp SIM và bật dữ liệu di động. Chờ hộp cảnh báo đếm ngược hết giờ rồi bấm Đồng ý.
   2. Mở Shizuku, bấm **Dừng** rồi **Khởi động lại**.
   3. Quay lại FloatTile và bấm **Cấp quyền Shizuku**.

   Nếu chưa gỡ giới hạn, những lệnh cần quyền cao như nút “Bật cửa sổ tự do bằng Shizuku” sẽ bị từ chối. Khi đó FloatTile sẽ hiện lời nhắc này.
8. **Nếu mọi cách đều không được:** dùng cửa sổ nổi có sẵn của HyperOS. Mở Đa nhiệm, nhấn giữ thẻ Claude rồi chọn biểu tượng cửa sổ nổi, hoặc dùng Thanh bên (Sidebar) của HyperOS.

## 7. Cấu trúc mã nguồn

```
FloatTile/app/src/main/java/com/floattile/
├── FloatTileService.kt     Ô cài đặt nhanh
├── LaunchActivity.kt       Màn hình trung chuyển trong suốt: mở ứng dụng, báo lỗi nếu có
├── FreeformLauncher.kt     Lõi: cách (a) reflection, cách (b) Shizuku, chế độ tự động
├── FreeformTester.kt       Kiểm tra thực tế bằng cửa sổ thử nghiệm
├── ProbeActivity.kt        Cửa sổ thử nghiệm, tự đo xem mình có đang nổi không
├── ShizukuHelper.kt        Trạng thái Shizuku, xin quyền, chạy lệnh
├── ShellService.kt         Dịch vụ chạy lệnh trong tiến trình có quyền ADB (UserService của Shizuku)
├── DumpsysParser.kt        Đọc mã task và chế độ cửa sổ từ dumpsys
├── WindowBounds.kt         Tính khung cửa sổ
├── TargetApp.kt            Tìm ứng dụng đích, gắn cờ mở
├── MainActivity.kt         Màn hình cài đặt
├── AppPickerActivity.kt    Chọn ứng dụng đích
├── DiagnosticsActivity.kt  Màn hình kiểm tra cửa sổ tự do
├── ShizukuGuideActivity.kt Hướng dẫn và cấp quyền Shizuku
├── BoundsPreviewView.kt    Hình xem trước kích thước và vị trí
├── FreeformEnv.kt          Đọc tùy chọn hệ thống
└── Prefs.kt                Lưu cài đặt
```

## 8. Tự build trên máy tính (không bắt buộc)

Cần cài JDK 17 và Android SDK. Sau đó chạy:

```bash
cd FloatTile
./gradlew assembleRelease
# File APK nằm ở app/build/outputs/apk/release/app-release.apk
```
