# TảiVideo – tải video từ link

Ứng dụng tải video từ **YouTube, Facebook, Instagram, TikTok, X (Twitter), Vimeo, Reddit, Twitch, SoundCloud…** và hơn 1000 trang web khác.

Cách dùng rất đơn giản:

1. Dán link video vào ô nhập, chọn chất lượng.
2. Bấm **Tải** → ngay bên dưới hiện khung xem trước, có vòng tròn báo tiến độ.
3. Video tạo xong sẽ phát được ngay trong khung. Bên dưới có nút **Tải về máy**.

Ứng dụng chạy ngay trên máy tính của bạn: không quảng cáo, không giới hạn số lần tải.

---

## 1. Cài đặt (chỉ làm một lần)

Bạn chỉ cần cài **Python** (bản 3.10 trở lên):

- Tải tại <https://www.python.org/downloads/>
- **Windows:** khi cài, nhớ tick ô **“Add python.exe to PATH”** ở màn hình đầu tiên.

Mọi thứ khác (bộ tải yt-dlp, ffmpeg để ghép hình và tiếng, Deno để đọc được YouTube) sẽ **tự động cài** ở lần chạy đầu.

## 2. Mở ứng dụng

| Máy | Cách mở |
|---|---|
| **Windows** | Bấm đúp vào tệp `run.bat` |
| **macOS / Linux** | Mở Terminal tại thư mục này, gõ `bash run.sh` rồi Enter |

- Lần đầu mất khoảng 1–2 phút để tải các thành phần cần thiết. Những lần sau mở rất nhanh.
- Trình duyệt sẽ tự mở trang **http://127.0.0.1:8000**. Nếu không thấy, hãy tự mở địa chỉ đó.
- **Giữ cửa sổ đen (cửa sổ dòng lệnh) mở** trong lúc dùng. Đóng cửa sổ đó là tắt ứng dụng.
- Mỗi lần mở, bộ tải sẽ tự cập nhật lên bản mới nhất. Nhờ vậy khi YouTube hay Facebook thay đổi, ứng dụng vẫn tải được.

## 3. Chọn chất lượng

| Lựa chọn | Khi nào nên dùng |
|---|---|
| Tốt nhất (tới 4K) | Muốn bản đẹp nhất. Tệp có thể rất nặng. |
| **Full HD 1080p** (mặc định) | Cân bằng giữa độ đẹp và dung lượng, mở được trên mọi thiết bị. |
| HD 720p / 480p / 360p | Cần tệp nhẹ để gửi qua tin nhắn hoặc mạng yếu. |
| Chỉ âm thanh MP3 | Chỉ lấy phần tiếng (nhạc, podcast…). |

Video được lưu dạng **MP4** (hình H.264, tiếng AAC – định dạng phổ biến nhất, mở được trên điện thoại, máy tính, TV).

## 4. Video riêng tư hoặc cần đăng nhập

Một số video (nhóm kín Facebook, tài khoản Instagram khóa, video giới hạn độ tuổi) chỉ xem được khi đã đăng nhập. Để tải những video này:

1. Cài tiện ích **“Get cookies.txt LOCALLY”** cho Chrome / Edge / Firefox.
2. Đăng nhập trang web đó (Facebook, Instagram…) trên trình duyệt.
3. Bấm vào tiện ích → **Export** để lưu tệp.
4. Đổi tên tệp thành `cookies.txt` và đặt vào **cùng thư mục với `app.py`**.
5. Tắt rồi mở lại ứng dụng.

> ⚠️ Tệp `cookies.txt` chứa thông tin đăng nhập của bạn. **Không gửi tệp này cho người khác.**

## 5. Dùng trên điện thoại (cùng mạng Wi-Fi)

Có thể mở ứng dụng từ điện thoại khi điện thoại và máy tính dùng chung Wi-Fi:

- **Windows:** mở cửa sổ dòng lệnh tại thư mục này rồi gõ
  `set HOST=0.0.0.0` → Enter, sau đó gõ `run.bat` → Enter.
- **macOS / Linux:** gõ `HOST=0.0.0.0 bash run.sh`

Trên điện thoại, mở `http://<địa-chỉ-IP-máy-tính>:8000` (ví dụ `http://192.168.1.5:8000`).
Chỉ làm vậy ở mạng nhà mình, không làm ở Wi-Fi công cộng.

## 6. Gặp lỗi?

| Hiện tượng | Cách xử lý |
|---|---|
| Báo “Chưa cài Python” | Cài Python theo mục 1, nhớ tick “Add python.exe to PATH”. |
| Link trước tải được, giờ lỗi | Tắt và mở lại ứng dụng để bộ tải tự cập nhật. |
| YouTube báo “xác minh bạn không phải máy” | Đợi vài phút rồi thử lại, hoặc dùng `cookies.txt` (mục 4). |
| “Video riêng tư hoặc cần đăng nhập” | Dùng `cookies.txt` (mục 4). |
| Không phát thử được trong khung xem trước | Một vài định dạng hiếm trình duyệt không phát được, nhưng nút **Tải về máy** vẫn dùng bình thường. |

Video tải về được lưu tạm trong thư mục `downloads/` và **tự xóa sau 2 giờ** (hoặc khi mở lại ứng dụng) để không làm đầy ổ đĩa. Tệp bạn đã bấm **Tải về máy** nằm trong thư mục *Tải xuống (Downloads)* của trình duyệt, không bị xóa.

## 7. Lưu ý bản quyền

Chỉ tải video của chính bạn, video được phép tải lại, hoặc để xem cá nhân. Không đăng lại hay dùng cho mục đích thương mại khi chưa được tác giả cho phép.

---

### Dành cho người muốn tìm hiểu thêm

```
video-downloader/
├── app.py            # Máy chủ: nhận link, gọi yt-dlp để tải, trả video về trình duyệt
├── requirements.txt  # Danh sách thư viện Python cần cài
├── run.bat / run.sh  # Tệp mở nhanh cho Windows / macOS-Linux
└── static/           # Giao diện: index.html, style.css, app.js
```

Có thể đổi một số thiết lập bằng biến môi trường (environment variable – giá trị cài đặt đặt trước khi chạy):

| Tên | Mặc định | Ý nghĩa |
|---|---|---|
| `PORT` | `8000` | Cổng mở trang (nếu bận, tự chọn cổng kế tiếp) |
| `HOST` | `127.0.0.1` | Đặt `0.0.0.0` để máy khác trong mạng truy cập được |
| `JOB_TTL` | `7200` | Số giây giữ tệp tạm trước khi tự xóa |
| `MAX_PARALLEL` | `3` | Số video được tải cùng lúc |
| `NO_BROWSER` | (trống) | Đặt `1` để không tự mở trình duyệt |
