plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Chaquopy: cho phép chạy Python ngay trên điện thoại, không cần mạng
    id("com.chaquo.python")
}

android {
    // Tên gói định danh app (giống "địa chỉ" duy nhất của app)
    namespace = "com.codelab.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.codelab.app"
        minSdk = 24          // Chạy được từ Android 7.0 trở lên
        targetSdk = 34       // Nhắm tới Android 14 (phiên bản mới)
        versionCode = 1
        versionName = "1.0"

        // Chaquopy cần chỉ định các loại chip (CPU) được hỗ trợ
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // Cho phép truy cập view trong code một cách an toàn (không cần findViewById)
        viewBinding = true
    }
}

// Cấu hình phiên bản Python được nhúng vào app
chaquopy {
    defaultConfig {
        version = "3.11"
    }
}

dependencies {
    // Thư viện nền tảng Android hiện đại (AndroidX)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Coroutines: chạy việc nặng (chạy code, gọi mạng) ở luồng nền cho mượt
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Gson: chuyển đổi dữ liệu project sang/dạng văn bản JSON để lưu vào máy
    implementation("com.google.code.gson:gson:2.11.0")

    // OkHttp: gửi code lên trình dịch online (dùng cho C++, C, Java)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Rhino: bộ chạy JavaScript thuần Java, chạy offline ngay trong app
    implementation("org.mozilla:rhino:1.7.14")

    // Thư viện kiểm thử (không bắt buộc cho app chạy)
    testImplementation("junit:junit:4.13.2")
}
