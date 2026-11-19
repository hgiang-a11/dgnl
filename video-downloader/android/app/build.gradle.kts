import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Khóa ký ứng dụng (tùy chọn): tạo tệp keystore.properties cạnh thư mục app với
// storeFile, storePassword, keyAlias, keyPassword. Nếu không có, dùng khóa debug.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.dgnl.taivideo"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "com.dgnl.taivideo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Mỗi loại chip điện thoại một tệp APK riêng cho nhẹ (bản gộp tất cả sẽ nặng tới ~200 MB).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // youtubedl-android chạy Python/ffmpeg từ thư mục thư viện gốc nên cần giải nén chúng khi cài.
    packaging {
        jniLibs { useLegacyPackaging = true }
        resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
    }

    // Dùng chung giao diện web với bản máy tính (thư mục video-downloader/static).
    sourceSets["main"].assets.srcDir("../../static")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { buildConfig = true }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { test ->
            // Bài kiểm thử tích hợp chỉ chạy khi có biến TV_YTDLP (đường dẫn yt-dlp) và TV_MEDIA_BASE.
            listOf("TV_YTDLP", "TV_MEDIA_BASE").forEach { k -> System.getenv(k)?.let { test.environment(k, it) } }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
