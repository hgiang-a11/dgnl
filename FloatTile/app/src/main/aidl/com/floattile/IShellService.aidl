// AIDL = ngôn ngữ mô tả "hợp đồng" giao tiếp giữa hai tiến trình (process).
// Dịch vụ này chạy bên trong tiến trình do Shizuku khởi động (mang quyền ADB "shell"),
// còn ứng dụng FloatTile gọi sang nó để chạy lệnh.
package com.floattile;

interface IShellService {
    // Mã 16777114 là mã riêng Shizuku quy định để yêu cầu dịch vụ tự đóng.
    void destroy() = 16777114;

    // Chạy một lệnh shell. Kết quả: dòng đầu là mã thoát (0 = thành công), phần sau là nội dung in ra.
    String exec(String command) = 1;
}
