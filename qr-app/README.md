# Tạo mã QR – ứng dụng Android

Ứng dụng biến **đường liên kết** hoặc **văn bản** thành mã QR, giao diện tiếng Việt, gọn gàng, dễ dùng.

## Tải và cài đặt

1. Tải tệp [`TaoMaQR.apk`](TaoMaQR.apk) về điện thoại (mở trang này trên điện thoại → bấm vào tệp → **Download / Tải xuống**).
2. Mở tệp vừa tải. Nếu máy hỏi, cho phép **"Cài ứng dụng không rõ nguồn gốc"** cho trình duyệt hoặc trình quản lý tệp.
3. Bấm **Cài đặt**.

Yêu cầu: Android 8.0 trở lên. Ứng dụng không cần mạng, không quảng cáo, không thu thập dữ liệu.

## Tính năng

- **Hai chế độ:** *Liên kết* và *Văn bản*. Mã QR hiện ra ngay khi bạn gõ.
- **Tự sửa liên kết:** chỉ cần gõ `facebook.com/trangcuaban`, ứng dụng tự thêm `https://` để mã mở đúng trang.
- **Hỗ trợ tiếng Việt có dấu** (và cả biểu tượng cảm xúc).
- **Nút Dán / Xóa** nhanh; khi dán đoạn chữ có lẫn một liên kết, ứng dụng tự lấy riêng liên kết.
- **6 màu cho mã** – đều là màu đậm trên nền trắng nên vẫn quét dễ dàng.
- **Lưu ảnh** vào Thư viện ảnh (thư mục `Pictures/TaoQR`).
- **Chia sẻ** ảnh mã QR qua Zalo, Messenger, Gmail…
- **Phóng to toàn màn hình**: tự tăng độ sáng tối đa và giữ màn hình luôn sáng để người khác quét.
- **Lịch sử**: các mã bạn đã lưu, chia sẻ hoặc phóng to được ghi lại để mở lại nhanh.
- **Nhận nội dung từ ứng dụng khác**:
  - Trong Chrome, YouTube, TikTok… bấm **Chia sẻ** → chọn **Tạo mã QR**.
  - Bôi đen chữ bất kỳ → chọn **Tạo mã QR** trong thanh công cụ hiện ra.
- Tự đổi **giao diện sáng/tối** theo cài đặt của máy, hiển thị đẹp cả trên máy tính bảng.

## Dành cho người lập trình

Mã nguồn viết bằng Kotlin + Jetpack Compose (Material 3), tạo mã QR bằng thư viện ZXing.

```
qr-app/
├─ app/src/main/java/app/taoqr/
│  ├─ MainActivity.kt      – mở ứng dụng, nhận nội dung được chia sẻ từ ứng dụng khác
│  ├─ QrViewModel.kt       – giữ trạng thái màn hình, tạo mã, lịch sử
│  ├─ QrGenerator.kt       – tạo ảnh mã QR
│  ├─ ImageActions.kt      – lưu ảnh, chia sẻ, đọc bộ nhớ tạm
│  ├─ Model.kt             – kiểu dữ liệu, xử lý liên kết, lưu lịch sử
│  └─ ui/                  – các màn hình và bảng màu
└─ app/src/test/           – kiểm tra tự động (mã tạo ra đọc ngược lại đúng nội dung)
```

Đóng gói lại tệp APK (cần JDK 17+ và Android SDK):

```bash
cd qr-app
echo "sdk.dir=/đường/dẫn/tới/android-sdk" > local.properties
./gradlew assembleRelease        # kết quả: app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # chạy kiểm tra tự động
```

Để ký bản phát hành bằng khóa riêng, tạo tệp `keystore.properties` (không đưa lên GitHub):

```properties
storeFile=/đường/dẫn/tới/khoa.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Nếu không có tệp này, bản phát hành sẽ được ký bằng khóa thử nghiệm (debug) của máy đang đóng gói.
