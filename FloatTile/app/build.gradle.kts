plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Số hiệu bản build: lấy từ GitHub Actions (mỗi lần build tăng 1) để bản mới cài đè được bản cũ.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.floattile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.floattile"
        minSdk = 26          // Android 8.0 trở lên
        targetSdk = 34       // Android 14
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Khóa ký (keystore = file chứa chữ ký số của ứng dụng).
    // Dùng chung một khóa cố định để bản build sau cài đè lên bản trước mà không phải gỡ.
    // Đây là khóa cho ứng dụng cá nhân, KHÔNG dùng để phát hành lên cửa hàng.
    signingConfigs {
        create("shared") {
            storeFile = file("floattile-shared.keystore")
            storePassword = "floattile"
            keyAlias = "floattile"
            keyPassword = "floattile"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false   // không rút gọn mã, tránh làm hỏng phần gọi qua reflection
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    buildFeatures {
        aidl = true          // AIDL = ngôn ngữ mô tả giao tiếp giữa hai tiến trình (dùng cho Shizuku)
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // Không để bước kiểm tra lint (công cụ soát lỗi tiềm ẩn) làm hỏng bản build.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Shizuku: cho phép ứng dụng chạy lệnh với quyền của ADB (quyền "shell") sau khi người dùng đồng ý.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
