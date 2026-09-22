# Sổ chi tiêu – app điện thoại Android

App ghi thu chi hằng ngày, làm theo bản Google Trang tính "Sổ chi tiêu" (phiên bản 8):
cùng bố cục, cùng màu sắc, cùng danh sách hạng mục và cách tính tổng.

**File cài đặt:** [`SoChiTieu.apk`](SoChiTieu.apk) (khoảng 55 KB, chạy trên Android 7.0 trở lên).

## Cách cài lên điện thoại

1. Mở trang này trên điện thoại, bấm vào `SoChiTieu.apk`, rồi bấm nút tải về (biểu tượng mũi tên
   hoặc "View raw").
2. Mở file vừa tải trong mục **Tải xuống**.
3. Nếu máy hỏi "Cho phép cài ứng dụng không rõ nguồn gốc", bấm **Cài đặt** → bật **Cho phép từ nguồn này**
   → quay lại và bấm **Cài đặt**.
4. Nếu Google Play Protect cảnh báo, bấm **Thông tin chi tiết** → **Vẫn cài đặt**.
   Cảnh báo này xuất hiện với mọi app không tải từ CH Play.

## Các trang trong app

| Trang | Tương ứng trong trang tính | Làm được gì |
|---|---|---|
| **Trang ngày** | Trang tên `2026-09-22` | Tổng thu, tổng chi, số dư có dấu +/− và đổi màu theo dư hay lỗ. Bấm **+** để ghi khoản mới, giờ tự điền. Bấm vào một khoản để sửa hoặc xóa (có nút Hoàn tác). Dùng mũi tên hoặc bấm vào ngày để xem ngày khác. |
| **Tổng hợp** | Trang `TONG_HOP` | Chọn kỳ xem Ngày, Tuần, Tháng, Năm và mốc ngày. Biểu đồ Cột đôi, Tròn chi, Tròn thu, mỗi hạng mục một màu cố định. Bảng thu chi theo hạng mục. |
| **Sổ cái** | Trang `SO_CAI` | Toàn bộ các khoản, xếp theo ngày mới nhất. Tìm theo hạng mục, ghi chú hoặc số tiền; lọc Thu hay Chi. Bấm vào tên ngày để mở trang ngày đó. |
| **Dữ liệu** | – | Sao lưu, khôi phục, xuất ra file bảng tính và nhập từ file bảng tính. |

Những việc trang tính phải làm bằng tay hoặc hẹn giờ thì app tự lo:

- **Tạo trang ngày mới lúc 0h:** app luôn mở đúng ngày hôm nay; để app mở qua 0h cũng tự sang ngày mới.
- **Dồn ngày cũ vào sổ cái:** mọi khoản nằm chung một chỗ và được giữ mãi, không cần dồn.
- **Chống đếm trùng:** mỗi khoản chỉ lưu một lần nên tổng hợp không bao giờ bị cộng hai lần.
  Khi nhập file bảng tính, khoản nào đã có sẵn sẽ được bỏ qua.

## Chuyển dữ liệu cũ từ Google Trang tính sang app

1. Trên Google Trang tính, mở trang `SO_CAI` (hoặc từng trang ngày), chọn
   **Tệp → Tải xuống → Giá trị được phân tách bằng dấu phẩy (.csv)**.
2. Chuyển file sang điện thoại (Zalo, Google Drive, email...).
3. Trong app, vào **Dữ liệu → Nhập từ file bảng tính** và chọn file đó.

Với trang ngày, app lấy ngày từ tên file (ví dụ `Sổ chi tiêu - 2026-09-22.csv`) hoặc từ dòng tiêu đề
"Thứ Ba, 22/09/2026" trong file.

## Dữ liệu được lưu ở đâu

Dữ liệu nằm trong bộ nhớ riêng của app trên điện thoại, không gửi đi đâu và không cần mạng.
Nếu gỡ app thì dữ liệu mất theo, vì vậy nên **Sao lưu toàn bộ** định kỳ và cất file sao lưu ở nơi khác.
Khi đổi máy: cài app trên máy mới rồi dùng **Khôi phục từ file sao lưu**.

## Dành cho người muốn sửa app

- Giao diện nằm trong thư mục `assets/` (`index.html`, `app.css`, `app.js`). Danh sách hạng mục và
  bảng màu ở đầu file `app.js`, giữ nguyên tên biến như trong code trang tính (`HANG_MUC`, `MAU_HANG_MUC`).
- Phần vỏ Android nằm ở `java/vn/hgiang/sochitieu/MainActivity.java`.
- Đóng gói lại bằng `./build.sh` trên máy Ubuntu hoặc Debian (các gói cần cài ghi ở đầu file).
  Có thể xem thử giao diện bằng cách mở `assets/index.html` trên trình duyệt máy tính.
- Thư mục `ky-ten/` chứa khóa ký app (mật khẩu `sochitieu`). Phải dùng đúng khóa này thì bản mới
  mới cài đè lên bản cũ mà không mất dữ liệu. Vì kho mã đang công khai nên ai cũng xem được khóa;
  chỉ cài file APK lấy từ chính kho mã này.
