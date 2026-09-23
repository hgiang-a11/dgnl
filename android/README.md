# Sắp Tới Nơi (app Android)

App báo thức theo vị trí: chọn nơi đến, chọn khoảng cách, app sẽ kêu chuông + rung + hiện màn hình báo khi bạn còn cách nơi đến đúng khoảng đó. App vẫn chạy khi tắt màn hình.

## Tải và cài

1. Vào trang **Releases** của repo này, chọn bản mới nhất, tải file `SapToiNoi.apk`.
2. Mở file vừa tải trên điện thoại. Nếu máy hỏi, cho phép "Cài ứng dụng từ nguồn không xác định".
3. Mở app, cho phép dùng **vị trí** và **thông báo**.
4. Khi app hỏi về pin, bấm **Cho phép** để điện thoại không tự tắt app lúc chạy ngầm.

## Cách dùng

- Chạm lên bản đồ hoặc gõ tên nơi đến rồi bấm **Tìm**.
- Kéo thanh trượt để chọn báo khi còn bao xa (100 m – 3 km).
- Bấm **Bắt đầu theo dõi**, rồi có thể tắt màn hình.
- Bấm **Kiểu báo** để chọn "Chuông + rung", "Chỉ rung", hoặc đổi nhạc chuông.
- Bấm **☆ Lưu** để lưu chỗ hay đi. Nhấn giữ vào chỗ đã lưu để xoá.

## Ghi chú kỹ thuật

- Viết bằng Java, không cần Google Play Services. Bản đồ dạng vẽ (vector) dùng MapLibre + OpenFreeMap (miễn phí, không cần mã đăng ký), dữ liệu OpenStreetMap. Tìm địa chỉ dùng Photon (gợi ý khi gõ) và Nominatim.
- File APK được GitHub Actions tự build mỗi khi thư mục `android/` thay đổi (xem `.github/workflows/android-apk.yml`).
- Khoá ký app (`app/saptoi.keystore`) được để chung trong repo để mọi bản build cài đè lên nhau được. Nếu muốn đưa lên CH Play, hãy tạo khoá mới và giữ bí mật.
