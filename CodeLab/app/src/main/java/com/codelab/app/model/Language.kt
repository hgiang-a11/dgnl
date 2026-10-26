package com.codelab.app.model

/**
 * Danh sách các ngôn ngữ lập trình mà app hỗ trợ.
 *
 * - runsOffline = true: chạy thật ngay trên điện thoại, KHÔNG cần mạng (Python, JavaScript).
 * - runsOffline = false: cần gửi lên trình dịch online để dịch/chạy (C++, C, Java).
 *
 * pistonLang / pistonVersion: tên và phiên bản dùng khi gọi trình dịch online Piston.
 */
enum class Language(
    val id: String,
    val displayName: String,
    val extension: String,
    val runsOffline: Boolean,
    val pistonLang: String,
    val pistonVersion: String,
    val template: String
) {
    PYTHON(
        id = "python",
        displayName = "Python",
        extension = "py",
        runsOffline = true,
        pistonLang = "python",
        pistonVersion = "3.10.0",
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
        pistonLang = "javascript",
        pistonVersion = "18.15.0",
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
        pistonLang = "c++",
        pistonVersion = "10.2.0",
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
        pistonLang = "c",
        pistonVersion = "10.2.0",
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
        pistonLang = "java",
        pistonVersion = "15.0.2",
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
