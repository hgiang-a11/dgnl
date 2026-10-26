# CodeLab — App học và chạy code trên điện thoại

Ứng dụng Android (viết bằng Kotlin) cho phép **viết code và chạy code ngay trên điện thoại**,
hỗ trợ nhiều ngôn ngữ phổ biến. Dành cho việc học lập trình mọi lúc mọi nơi.

---

## 1. Tính năng chính

- **Giao diện tối** toàn app, phong cách giống các app lập trình hiện đại.
- **Màn hình chính**: các dự án hiển thị dạng lưới thẻ (2 ô mỗi hàng) với huy hiệu màu theo ngôn ngữ,
  nhãn Offline/Cần mạng, thời gian sửa gần nhất; nút **Dự án mới** để tạo dự án.
- **Tạo dự án**: hộp thoại nổi giữa màn hình — chọn ngôn ngữ, đặt tên và mô tả.
- **Màn hình soạn thảo** (chia trên – dưới):
  - Phần **trên**: ô viết code, có **số dòng** và **tô màu cú pháp** (từ khóa, chuỗi, số, chú thích).
  - Thanh nút ở **giữa**: `Chạy` ▶, `Build + Chạy`, `Build` (chỉ kiểm tra lỗi, không chạy), `Hoàn tác`, `Làm lại`, `Lưu`.
  - Phần **dưới**: khung kết quả, kèm trạng thái (hoàn thành / lỗi / không có mạng) và thời gian chạy.
  - Bố cục **cố định**: mở bàn phím thì bàn phím đè lên khung kết quả, không đẩy màn hình lên.
- **Tự động lưu** code khi rời màn hình; dữ liệu lưu trong máy nên **không mất khi tắt app**.

## 2. Ngôn ngữ và cách chạy

| Ngôn ngữ | Cách chạy | Cần Internet? |
|----------|-----------|----------------|
| Python | Chạy thật ngay trong app (thư viện Chaquopy) | **Không** — offline hoàn toàn |
| JavaScript | Chạy thật ngay trong app (thư viện Rhino) | **Không** — offline hoàn toàn |
| C++ | Gửi lên trình dịch online (Wandbox) | **Có** |
| C | Gửi lên trình dịch online (Wandbox) | **Có** |
| Java | Gửi lên trình dịch online (Wandbox) | **Có** |

> Với C++, C, Java: nếu **không có mạng**, app sẽ hiện thông báo *"Không có kết nối Internet"* và hướng dẫn bật mạng.
> Python và JavaScript luôn chạy được kể cả khi tắt hoàn toàn Internet.

Vì sao C++/Java phải chạy online? Để dịch (biên dịch) 2 ngôn ngữ này **hoàn toàn offline trên điện thoại**
cần nhúng cả một bộ biên dịch rất lớn vào app — đây là một dự án riêng rất tốn công. Giải pháp hiện tại
gửi code lên máy chủ dịch miễn phí **Wandbox** (wandbox.org, không cần đăng ký khóa; trước đây dùng Piston nhưng dịch vụ này đã ngừng mở công khai từ 15/2/2026). Có thể bổ sung chạy
offline cho các ngôn ngữ này về sau mà không phải sửa lại giao diện.

---

## 3. Cách tạo file APK (cài lên điện thoại)

### Cách nhanh nhất — Tải APK do GitHub build sẵn (không cần cài gì)
Mỗi khi code trong thư mục `CodeLab` thay đổi, GitHub tự động build APK, **cài thử trên máy ảo
Android 14** (kiểm tra app mở được, Python/JavaScript chạy đúng) rồi mới đăng lên. Quy trình ở
`.github/workflows/build-apk.yml`, mất khoảng 15–20 phút.

1. Mở trang tải bằng trình duyệt Chrome trên điện thoại:
   https://github.com/hgiang-a11/dgnl/releases/latest
   (hoặc vào trang chính của kho mã, kéo xuống mục **Releases** và bấm vào **CodeLab - Tải APK**).
2. Bấm link **"BẤM VÀO ĐÂY ĐỂ TẢI CodeLab.apk"** (hoặc file **`CodeLab.apk`** trong mục Assets).
3. Mở file vừa tải → điện thoại sẽ hỏi cho phép cài app từ nguồn không xác định → bấm **Cho phép** → **Cài đặt**.

Muốn build lại bằng tay: vào tab **Actions** → chọn **Build APK** → bấm **Run workflow**.
Nếu không thấy ở Releases, có thể tải trong mục **Artifacts** ở cuối trang của lần build
(file nén .zip, giải nén ra sẽ có APK).

### Yêu cầu (nếu tự build trên máy tính)
- Máy tính cài **Android Studio** (bản mới, ví dụ Koala/Ladybug trở lên — đã kèm sẵn bộ công cụ Android SDK).
- Máy tính cài **Python 3.11** (Chaquopy cần đúng phiên bản này để xử lý mã Python của app khi build).
- Lần build đầu tiên **cần Internet** để Android Studio tự tải thư viện và bộ Python của Chaquopy về.
  Sau khi cài app rồi thì Python chạy offline bình thường.
- App được ký bằng khóa cố định `app/codelab.keystore` (để bản mới cài đè được lên bản cũ). Khóa này
  để công khai trong kho mã nên chỉ phù hợp app cá nhân/học tập; nếu đưa lên Play Store hãy tạo khóa
  riêng và giữ bí mật.

### Cách 1 — Dùng Android Studio (dễ nhất)
1. Mở Android Studio → **File > Open** → chọn thư mục `CodeLab` này.
2. Chờ Android Studio tải thư viện xong (thanh dưới cùng chạy hết).
3. Cắm điện thoại (đã bật *gỡ lỗi USB*) hoặc tạo máy ảo, rồi bấm nút **Run ▶**. App sẽ cài và chạy.
4. Để xuất **file APK** để chia sẻ/cài tay: menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
   File APK sẽ nằm ở: `app/build/outputs/apk/debug/app-debug.apk`.

### Cách 2 — Dùng dòng lệnh
Trong thư mục `CodeLab` (máy đã cài Android SDK và đặt biến môi trường `ANDROID_HOME`):

```bash
# Tạo file APK bản thử nghiệm (debug)
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Tạo file APK bản phát hành (đã ký sẵn, cài lên máy thật được)
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk

# Chạy kiểm thử trên máy ảo/điện thoại đang kết nối
./gradlew connectedDebugAndroidTest
```

> Nếu báo lỗi *"SDK location not found"*: hãy mở dự án bằng Android Studio một lần (nó tự tạo file
> `local.properties` chỉ đường tới Android SDK), hoặc tự tạo file `local.properties` với nội dung
> `sdk.dir=/duong/dan/toi/Android/Sdk`.

---

## 4. Cấu trúc thư mục chính

```
CodeLab/
├── app/
│   ├── build.gradle.kts          # Khai báo thư viện và cấu hình app
│   └── src/main/
│       ├── AndroidManifest.xml   # Khai báo quyền (Internet) và màn hình chính
│       ├── java/com/codelab/app/
│       │   ├── MainActivity.kt           # Màn hình gốc, điều hướng
│       │   ├── CodeLabApp.kt             # Khởi động Python khi mở app
│       │   ├── model/                    # Dữ liệu: Project, Language
│       │   ├── data/ProjectRepository.kt # Lưu/đọc project (offline, trong máy)
│       │   ├── exec/                     # Các bộ chạy code (Python, JS, online)
│       │   ├── editor/                   # Ô soạn thảo: tô màu, số dòng, undo/redo
│       │   └── ui/                       # Màn hình chính, hộp thoại, màn hình soạn thảo
│       ├── python/code_runner.py         # Mã Python nhúng để chạy code offline
│       └── res/                          # Giao diện (layout), màu, icon
└── README.md
```

## 5. Phiên bản công cụ (đã kiểm tra tương thích)

- Gradle 8.9 · Android Gradle Plugin 8.6.1 · Kotlin 1.9.24
- compileSdk / targetSdk 34 (Android 14) · minSdk 24 (chạy từ Android 7.0)
- Chaquopy 17.1.0 (Python 3.11, hỗ trợ điện thoại bộ nhớ trang 16 KB) · Rhino 1.7.13 · OkHttp 4.12.0
  (Rhino giữ 1.7.13 vì bản 1.7.14 làm app sập trên Android)

## 6. Ghi chú / hướng mở rộng

- Muốn thêm ngôn ngữ mới: thêm một mục trong `model/Language.kt` (và chọn chạy offline hay online).
- Muốn đổi máy chủ dịch online: sửa đường dẫn trong `exec/OnlineExecutor.kt`.
- Có thể thêm tính năng nhập dữ liệu bàn phím (stdin), chia sẻ code, đổi giao diện sáng/tối...
