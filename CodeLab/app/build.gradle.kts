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
        versionCode = 5
        versionName = "1.4"

        // Chaquopy cần chỉ định các loại chip (CPU) được hỗ trợ:
        // arm64-v8a (đa số điện thoại hiện nay), armeabi-v7a (máy cũ 32-bit), x86_64 (máy ảo)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        // Bộ chạy kiểm thử trên máy ảo/điện thoại (dùng cho thư mục androidTest)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Khóa ký app CỐ ĐỊNH, để mọi bản build (kể cả trên GitHub) có cùng chữ ký
    // -> cài bản mới đè lên bản cũ được. Khóa này để công khai trong kho mã nên chỉ
    // phù hợp app cá nhân/học tập; nếu đưa lên Play Store cần tạo khóa riêng và giữ bí mật.
    signingConfigs {
        create("codelab") {
            storeFile = file("codelab.keystore")
            storeType = "pkcs12"
            storePassword = "codelab123"
            keyAlias = "codelab"
            keyPassword = "codelab123"
            // Ký đủ 3 kiểu chữ ký để mọi trình cài đặt/quét bảo mật đều nhận
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("codelab")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("codelab")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        jniLibs {
            // Nén thư viện máy (.so) và giải nén khi cài: cách đóng gói truyền thống,
            // tương thích với mọi điện thoại và làm file APK nhẹ hơn
            useLegacyPackaging = true
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

    // Rhino: bộ chạy JavaScript thuần Java, chạy offline ngay trong app.
    // Giữ 1.7.13: bản 1.7.14 dùng lớp javax.lang.model.SourceVersion không có trên Android,
    // sẽ làm app sập khi chạy JavaScript.
    implementation("org.mozilla:rhino:1.7.13")

    // Thư viện kiểm thử (không bắt buộc cho app chạy)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    // Espresso: tự động bấm nút, gõ chữ trong kiểm thử giao diện
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
