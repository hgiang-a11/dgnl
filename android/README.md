# Sắp Tới Nơi (app Android)

App báo thức theo vị trí: chọn nơi đến, chọn khoảng cách, app sẽ kêu chuông + rung + hiện màn hình báo khi bạn còn cách nơi đến đúng khoảng đó. App vẫn chạy khi tắt màn hình.

## Tải và cài

1. Vào trang **Releases** của repo này, chọn bản mới nhất, tải file `SapToiNoi.apk`.
2. Mở file vừa tải trên điện thoại. Nếu máy hỏi, cho phép "Cài ứng dụng từ nguồn không xác định".
3. Mở app, cho phép dùng **vị trí** và **thông báo**.
4. Khi app hỏi về pin, bấm **Cho phép** để điện thoại không tự tắt app lúc chạy ngầm.

## Cách dùng

- Chọn nơi đến bằng một trong các cách:
  - Chạm vào icon địa điểm trên bản đồ (bệnh viện, quán ăn, trạm xe buýt...) để chọn đúng chỗ đó.
  - Chạm vào chỗ trống trên bản đồ để ghim; app tự tìm địa chỉ bằng chữ.
  - Gõ tên / địa chỉ vào ô tìm kiếm. Kết quả gần bạn được xếp trước, có loại địa điểm và khoảng cách.
- Với nơi rộng (trung tâm thương mại, bệnh viện, trường...), nếu bản đồ có dữ liệu cổng thì app hiện thêm "Cổng 1, Cổng 2..." để chọn.
- Kéo bảng phía dưới lên / xuống để xem thêm bản đồ hoặc xem cài đặt.
- Kéo thanh trượt hoặc bấm nút − / + để chọn báo khi còn bao xa (10 m – 3 km, mỗi nấc 10 m; giữ nút để đổi nhanh).
- Bấm **Bắt đầu theo dõi**, rồi có thể tắt màn hình.
- Bấm **Kiểu báo** để chọn "Chuông + rung", "Chỉ rung", hoặc đổi nhạc chuông.
- Bấm **Lưu**, hoặc nhấn giữ lên bản đồ, để thêm điểm đánh dấu (ngôi sao vàng trên bản đồ). Nhấn giữ nút ngôi sao dưới ô tìm kiếm để xoá.

## Ghi chú kỹ thuật

- Viết bằng Java, không cần Google Play Services. Bản đồ dạng vẽ (vector) dùng MapLibre + OpenFreeMap (miễn phí, không cần mã đăng ký), dữ liệu OpenStreetMap. Tìm địa chỉ dùng Photon (gợi ý khi gõ, đổi toạ độ thành địa chỉ) và Nominatim; cổng / lối vào lấy từ Overpass.
- Icon địa điểm dùng bộ Material Icons của Google (giấy phép Apache 2.0).
- File APK được GitHub Actions tự build mỗi khi thư mục `android/` thay đổi (xem `.github/workflows/android-apk.yml`).
- Khoá ký app (`app/saptoi.keystore`) được để chung trong repo để mọi bản build cài đè lên nhau được. Nếu muốn đưa lên CH Play, hãy tạo khoá mới và giữ bí mật.
