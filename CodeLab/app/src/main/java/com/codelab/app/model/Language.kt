package com.codelab.app.model

/**
 * Danh sách các ngôn ngữ lập trình mà app hỗ trợ.
 *
 * - runsOffline = true: chạy thật ngay trên điện thoại, KHÔNG cần mạng (Python, JavaScript).
 * - runsOffline = false: cần gửi lên trình dịch online để dịch/chạy (C++, C, Java).
 *
 * onlineLanguage: tên ngôn ngữ trên trình dịch online Wandbox (wandbox.org).
 * preferredCompilers: trình dịch ưu tiên chọn trên Wandbox; nếu không có thì app
 * tự chọn bản mới nhất của ngôn ngữ đó.
 */
enum class Language(
    val id: String,
    val displayName: String,
    val extension: String,
    val runsOffline: Boolean,
    val onlineLanguage: String,
    val preferredCompilers: List<String>,
    val template: String
) {
    PYTHON(
        id = "python",
        displayName = "Python",
        extension = "py",
        runsOffline = true,
        onlineLanguage = "Python",
        preferredCompilers = emptyList(),
        template = """# Chương trình Python đầu tiên
print("Xin chào từ Python!")

ten = "bạn"
print("Chào", ten)

tong = 0
for i in range(1, 6):
    tong += i
print("Tổng 1..5 =", tong)
"""
    ),

    JAVASCRIPT(
        id = "javascript",
        displayName = "JavaScript",
        extension = "js",
        runsOffline = true,
        onlineLanguage = "JavaScript",
        preferredCompilers = emptyList(),
        template = """// Chương trình JavaScript đầu tiên
console.log("Xin chào từ JavaScript!");

var ten = "bạn";
console.log("Chào " + ten);

var tong = 0;
for (var i = 1; i <= 5; i++) {
    tong += i;
}
console.log("Tổng 1..5 = " + tong);
"""
    ),

    CPP(
        id = "cpp",
        displayName = "C++",
        extension = "cpp",
        runsOffline = false,
        onlineLanguage = "C++",
        preferredCompilers = listOf("gcc-head", "clang-head"),
        template = """// Chương trình C++ đầu tiên
#include <iostream>
using namespace std;

int main() {
    cout << "Xin chào từ C++!" << endl;

    int tong = 0;
    for (int i = 1; i <= 5; i++) {
        tong += i;
    }
    cout << "Tong 1..5 = " << tong << endl;
    return 0;
}
"""
    ),

    C(
        id = "c",
        displayName = "C",
        extension = "c",
        runsOffline = false,
        onlineLanguage = "C",
        preferredCompilers = listOf("gcc-head-c", "clang-head-c"),
        template = """// Chương trình C đầu tiên
#include <stdio.h>

int main() {
    printf("Xin chao tu C!\n");

    int tong = 0;
    for (int i = 1; i <= 5; i++) {
        tong += i;
    }
    printf("Tong 1..5 = %d\n", tong);
    return 0;
}
"""
    ),

    JAVA(
        id = "java",
        displayName = "Java",
        extension = "java",
        runsOffline = false,
        onlineLanguage = "Java",
        // Wandbox không có bản "head" cho Java -> app tự chọn OpenJDK mới nhất
        preferredCompilers = emptyList(),
        template = """// Chương trình Java đầu tiên
public class Main {
    public static void main(String[] args) {
        System.out.println("Xin chao tu Java!");

        int tong = 0;
        for (int i = 1; i <= 5; i++) {
            tong += i;
        }
        System.out.println("Tong 1..5 = " + tong);
    }
}
"""
    );

    companion object {
        /** Tìm ngôn ngữ theo mã id; nếu không thấy thì mặc định Python. */
        fun fromId(id: String): Language = entries.firstOrNull { it.id == id } ?: PYTHON
    }
}
