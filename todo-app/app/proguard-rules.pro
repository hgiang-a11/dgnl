# Giu lai ten lop cua app de crash log de doc
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose runtime da co rule rieng trong AAR, khong can them.
# Chi giu cac lop du lieu duoc doc/ghi bang org.json (dung reflection = khong)
-keep class com.dgnl.taskflow.data.** { *; }

# Kotlin metadata
-dontwarn kotlinx.coroutines.**
