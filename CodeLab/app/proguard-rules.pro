# Giữ lại các lớp của Rhino (bộ chạy JavaScript) để không bị xóa nhầm khi tối ưu
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**

# Giữ lại các lớp model (dữ liệu project) để Gson đọc/ghi đúng
-keep class com.codelab.app.model.** { *; }
