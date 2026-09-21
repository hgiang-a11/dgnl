# TaskFlow — App quản lý công việc cho Android

App to-do list dạng bảng 3 cột **kéo thả**, giao diện nền đen, viết bằng **Kotlin + Jetpack Compose**
(mã chạy thẳng trên máy, không qua lớp trung gian nào nên rất mượt, không giật).

---

## Tải file APK về cài

Sau khi mã nguồn được đẩy lên GitHub, máy chủ của GitHub sẽ tự động dựng file APK.

**Cách 1 — tải từ mục Releases (dễ nhất):**
mở trang repo trên GitHub → mục **Releases** → bản `taskflow-latest` → tải **`TaskFlow-release.apk`**.

**Cách 2 — tải từ tab Actions:**
tab **Actions** → chọn lần chạy **Build TaskFlow APK** mới nhất → kéo xuống mục **Artifacts** →
tải gói `TaskFlow-apk`, giải nén ra sẽ thấy file APK.

**Cài lên điện thoại:** mở file APK vừa tải → Android sẽ hỏi quyền → vào
*Cài đặt → Ứng dụng → Quyền đặc biệt → Cài ứng dụng không rõ nguồn gốc* → bật cho trình duyệt
hoặc app quản lý file đang dùng → quay lại và bấm Cài đặt.

> Hai bản APK khác nhau ở chỗ: `TaskFlow-release.apk` đã được tối ưu, nhẹ và chạy nhanh hơn — nên
> dùng bản này. `TaskFlow-debug.apk` chỉ để thử nghiệm.

---

## Các tính năng

### Nhiều danh sách công việc
- Tạo (nút **+** ở thanh trên cùng), đổi tên, đổi màu nhãn, nhân bản, sắp xếp lại thứ tự,
  xoá (có nút **Hoàn tác**).
- Mỗi danh sách hiển thị thanh tiến độ, số việc theo từng trạng thái và số việc quá hạn.
- Ba ô thống kê nhanh ở đầu màn hình: tổng việc, đang làm, quá hạn.

### Tìm kiếm
Bấm kính lúp là mở ra **một trang tìm kiếm riêng chiếm trọn màn hình** — mũi tên quay lại ở góc
trái, ô gõ chữ trống trơn không khung viền, bàn phím tự bật lên, kết quả hiện ngay bên dưới.
Chạm một kết quả là nhảy thẳng tới nó.

Phạm vi tìm bám đúng chỗ bạn đang đứng:

| Đang ở màn hình | Tìm được gì |
|---|---|
| Trang chủ | Chỉ tìm **tên các danh sách** |
| Trong một danh sách | Chỉ tìm **việc của riêng danh sách đó** (theo tên hoặc mô tả) |

### Thanh tiến độ ba phần
Thanh dưới tên mỗi danh sách ở trang chủ chia đúng theo tỉ lệ số việc:

| Phần | Ý nghĩa |
|---|---|
| **Tô đặc** (màu nhãn) | Việc **đã làm** |
| **Vạch chéo đang chạy** (cam) | Việc **đang làm** |
| **Để trống** (xám) | Việc **chưa làm** |

### Bảng 3 mục kéo thả
- Ba mục cố định: **Chưa làm → Đang làm → Đã làm**.
- **Giữ ngón tay lên một thẻ khoảng nửa giây** rồi kéo: thẻ nhấc lên bay theo tay, mục đích sáng
  viền, có vạch màu chỉ đúng chỗ thẻ sẽ rơi vào.
- Kéo ra sát mép thì bảng **tự trượt** để đi tiếp.
- **Chạm vào vòng tròn bên trái thẻ** là đổi trạng thái ngay tại chỗ
  (Chưa làm → Đang làm → Đã làm → quay lại Chưa làm), không cần mở ra sửa rồi lưu.
- Hoặc bấm dấu **⋮** trên thẻ để chọn thẳng trạng thái muốn chuyển sang.
- Thả trong cùng một mục để đổi thứ tự các việc.

### Hai kiểu giao diện, đổi bằng một nút
Nút ba vạch ở thanh trên cùng (cạnh kính lúp) đổi qua lại giữa hai kiểu — biểu tượng xoay 90 độ
theo kiểu đang dùng:

| Kiểu | Cách hiển thị |
|---|---|
| **Cột dọc** | Ba cột đứng cạnh nhau, vuốt ngang để đổi cột |
| **Hàng ngang** | Ba khối xếp chồng, nhìn thấy cả ba cùng lúc |

Ở cả hai kiểu, **việc luôn xếp dọc từ trên xuống** và chiếm trọn bề ngang của khối.

Ở kiểu **hàng ngang**, ba khối **tự chia nhau đúng chiều cao màn hình** nên không phải lướt cả
trang. Khối nào nhiều việc thì được chia nhiều chiều cao hơn, khối trống chỉ lấy một phần nhỏ.
Nếu việc trong một khối nhiều quá không hiện hết thì **chính khối đó cuộn lên xuống bên trong**,
các khối khác vẫn đứng yên.

Kiểu đang chọn được **ghi nhớ riêng cho từng danh sách** — lần sau mở lại vẫn đúng kiểu đó.

Mục nào chưa có việc thì hiện **một ô duy nhất**, chạm vào là thêm việc luôn. Khi đã có việc rồi
thì ô đó biến mất, thêm việc bằng dấu **+** ở đầu mục.

### Công việc
- Tên việc, mô tả, **ngày bắt đầu** (bỏ trống thì tự lấy ngày tạo việc), **ngày đến hạn** (không bắt buộc).
- Mức ưu tiên: Thấp / Bình thường / Cao / Khẩn cấp — hiện thành vạch màu bên trái thẻ.
- Thẻ tự đổi màu cảnh báo khi **quá hạn** (viền đỏ) hoặc **đến hạn hôm nay** (nhãn vàng).
- Việc đã xong được gạch ngang chữ.
- Nhân bản việc, xoá việc (có Hoàn tác), xem giờ tạo / giờ sửa / giờ hoàn thành.

### Lọc và sắp xếp
- Lọc nhanh: Tất cả · Hôm nay · Quá hạn · Ưu tiên cao.
- Sắp xếp: thủ công · hạn gần nhất · mức ưu tiên · mới tạo trước · tên A→Z.
- Tìm việc theo tên hoặc mô tả ngay trong bảng.
- Lệnh **dời tất cả việc quá hạn sang hôm nay** và **xoá sạch cột Đã làm** (đều có Hoàn tác).

---

## Vì sao app chạy mượt

| Việc đã làm | Lý do |
|---|---|
| Kotlin + Jetpack Compose chạy trực tiếp trên máy | Không dùng WebView hay lớp trung gian như React Native / Flutter bridge |
| Dữ liệu nằm sẵn trong bộ nhớ, ghi file ở luồng nền | Giao diện không bao giờ phải đợi đọc/ghi ổ cứng |
| Dùng `LazyColumn` (chỉ dựng những thẻ đang nhìn thấy) | Danh sách vài trăm việc vẫn cuộn êm |
| Lúc kéo thả, vị trí ngón tay chỉ được đọc ở bước đo đạc | Không vẽ lại toàn màn hình 60 lần mỗi giây |
| Hiệu ứng chạy bằng `graphicsLayer` (lớp vẽ riêng) | Thẻ phóng to/thu nhỏ mà không phải đo lại bố cục |
| Bản release bật rút gọn mã và tài nguyên (R8) | File APK nhỏ, khởi động nhanh hơn |
| Không dùng thư viện nặng | Chỉ Compose + thư viện chuẩn của Android |

---

## Dữ liệu được lưu ở đâu

Toàn bộ nằm trong một file `taskflow.json` ở vùng nhớ riêng của app trên máy bạn.
Không có tài khoản, không gửi đi đâu, không cần mạng. File được ghi theo kiểu an toàn
(ghi ra file tạm rồi mới đổi tên) nên mất điện giữa chừng cũng không hỏng dữ liệu.

---

## Tự build trên máy tính

Cần **Android Studio** (bản Ladybug trở lên) hoặc Android SDK kèm JDK 17.

Cách dễ nhất: mở thư mục `todo-app` bằng Android Studio rồi bấm nút Run — Android Studio tự lo
phần còn lại.

Nếu thích dùng dòng lệnh (cần cài sẵn Gradle 8.9 trở lên):

```bash
cd todo-app
gradle wrapper          # chỉ cần chạy một lần, tạo ra ./gradlew
./gradlew :app:assembleRelease
# File nằm ở: app/build/outputs/apk/release/app-release.apk
```

Cắm điện thoại vào máy và cài thẳng:

```bash
./gradlew :app:installRelease
```

> Kho mã không kèm file `gradle-wrapper.jar` (file nhị phân), nên lần đầu cần chạy `gradle wrapper`
> hoặc để Android Studio tự tạo.

---

## Thông số kỹ thuật

| Mục | Giá trị |
|---|---|
| Ngôn ngữ | Kotlin 2.0.21 |
| Giao diện | Jetpack Compose (Material 3) |
| Android thấp nhất | 8.0 (API 26) |
| Android biên dịch theo | 15 (API 35) |
| Công cụ build | Gradle 8.9 + Android Gradle Plugin 8.7.3 |
| Lưu trữ | File JSON trong bộ nhớ riêng của app |

### Cấu trúc thư mục

```
todo-app/app/src/main/java/com/dgnl/taskflow/
├── MainActivity.kt          màn hình gốc
├── TaskFlowApp.kt           khởi tạo kho dữ liệu dùng chung
├── data/
│   ├── Models.kt            Board, Task, TaskStatus, Priority
│   └── TaskRepository.kt    đọc/ghi JSON + phát dữ liệu cho giao diện
└── ui/
    ├── AppRoot.kt           điều hướng giữa 3 màn hình
    ├── home/                danh sách các to-do list
    ├── board/               bảng 3 cột + toàn bộ phần kéo thả
    ├── task/                màn hình nhập/sửa việc
    ├── common/              thành phần dùng chung, định dạng ngày tháng
    └── theme/               bảng màu nền đen
```
