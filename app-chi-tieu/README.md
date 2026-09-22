# Sổ chi tiêu – app điện thoại Android

App ghi thu chi hằng ngày, làm theo bản Google Trang tính "Sổ chi tiêu" (phiên bản 8):
cùng danh sách hạng mục, cùng màu từng hạng mục và cùng cách tính tổng.

**File cài đặt:** [`SoChiTieu.apk`](SoChiTieu.apk) – phiên bản 1.1, chạy trên Android 7.0 trở lên.

## Cách cài lên điện thoại

1. Mở trang này trên điện thoại, bấm vào `SoChiTieu.apk`, rồi bấm nút tải về (biểu tượng mũi tên
   hoặc "View raw").
2. Mở file vừa tải trong mục **Tải xuống**.
3. Nếu máy hỏi "Cho phép cài ứng dụng không rõ nguồn gốc", bấm **Cài đặt** → bật **Cho phép từ nguồn này**
   → quay lại và bấm **Cài đặt**.
4. Nếu Google Play Protect cảnh báo, bấm **Thông tin chi tiết** → **Vẫn cài đặt**.
   Cảnh báo này xuất hiện với mọi app không tải từ CH Play.

Đã cài bản cũ thì cứ cài đè bản mới lên, dữ liệu vẫn giữ nguyên.

## Các trang trong app

**Trang ngày** – các khoản trong một ngày (tương ứng trang `2026-09-22` của trang tính).
- Dải ngày trên cùng: hôm nay có nhãn **HÔM NAY** và màu đậm, ngày khác hiện nhạt hơn.
- Bấm mũi tên để xem ngày trước hoặc sau, bấm vào ngày để chọn ngày bất kỳ.
  Muốn về hôm nay: bấm lại nút **Trang ngày** ở thanh dưới, hoặc bấm nút quay lại của điện thoại.
- Bấm **+** để ghi khoản mới, giờ tự điền. Bấm vào một khoản để sửa hoặc xóa (có nút Hoàn tác).

**Tổng hợp** – tình hình thu chi (tương ứng trang `TONG_HOP`).
- Mặc định xem hôm nay với biểu đồ tròn. Bấm vào dòng thời gian để đổi sang tuần, tháng, năm
  hoặc một ngày cụ thể; mũi tên hai bên để lùi hoặc tiến từng kỳ.
- Hai ô **Chi tiêu** và **Thu nhập**: bấm ô nào thì biểu đồ và danh sách hiện theo ô đó.
  Bên dưới là số dư trong kỳ và mức tăng giảm so với kỳ trước.
- Nút **Phân bổ** hiện biểu đồ tròn theo hạng mục; nút biểu tượng cột hiện biểu đồ cột thu chi
  theo giờ (đủ 24 giờ), theo ngày hoặc theo tháng.
- Bấm vào một hạng mục trong danh sách để xem từng khoản của hạng mục đó.
- Bấm con mắt để che số tiền khi đưa máy cho người khác xem.

**Cài đặt**
- **Màu giao diện:** 8 màu để chọn, thanh trạng thái của điện thoại đổi theo.
- **Xuất file Excel:** file .xlsx có trang `SO_CAI` (các cột NGÀY, GIỜ, LOẠI, HẠNG MỤC, SỐ TIỀN, GHI CHÚ)
  và trang `TONG_HOP` (tổng thu, tổng chi, số dư từng tháng).
- **Xuất file JSON:** dùng để nhập lại vào app khi đổi máy hoặc cài lại app.
- **Nhập dữ liệu:** nhận file JSON xuất từ app, file Excel (.xlsx) hoặc .csv. Khoản nào đã có sẽ được bỏ qua
  nên nhập lại nhiều lần cũng không bị đếm trùng.

## Chuyển dữ liệu cũ từ Google Trang tính sang app

1. Trên Google Trang tính, chọn **Tệp → Tải xuống → Microsoft Excel (.xlsx)**. File tải về chứa mọi trang.
2. Chuyển file sang điện thoại (Zalo, Google Drive, email...).
3. Trong app, vào **Cài đặt → Nhập dữ liệu** và chọn file đó.

App đọc cả các trang ngày (tên dạng `2026-09-22`) lẫn trang `SO_CAI`. Giống code trang tính,
ngày nào còn trang riêng thì dòng của ngày đó trong sổ cái được bỏ qua để không cộng hai lần.
Trang `TONG_HOP` và `MAU` không chứa khoản nào nên được bỏ qua.

## Dữ liệu được lưu ở đâu

Dữ liệu nằm trong bộ nhớ riêng của app trên điện thoại, không gửi đi đâu và không cần mạng.
Nếu gỡ app thì dữ liệu mất theo, vì vậy nên **Xuất file JSON** định kỳ và cất file ở nơi khác.
Khi đổi máy: cài app trên máy mới rồi dùng **Nhập dữ liệu** với file JSON đó.

## Dành cho người muốn sửa app

- Giao diện nằm trong thư mục `assets/` (`index.html`, `app.css`, `app.js`). Danh sách hạng mục, bảng màu
  và biểu tượng từng hạng mục ở đầu file `app.js` (`HANG_MUC`, `MAU_HANG_MUC`, `BIEU_TUONG`).
- Phần vỏ Android nằm ở `java/vn/hgiang/sochitieu/MainActivity.java`.
- Đóng gói lại bằng `./build.sh` trên máy Ubuntu hoặc Debian (các gói cần cài ghi ở đầu file).
  Có thể xem thử giao diện bằng cách mở `assets/index.html` trên trình duyệt máy tính.
- Mỗi lần phát hành bản mới, tăng `versionCode` và `versionName` trong `AndroidManifest.xml`.
- Thư mục `ky-ten/` chứa khóa ký app (mật khẩu `sochitieu`). Phải dùng đúng khóa này thì bản mới
  mới cài đè lên bản cũ mà không mất dữ liệu. Vì kho mã đang công khai nên ai cũng xem được khóa;
  chỉ cài file APK lấy từ chính kho mã này.
