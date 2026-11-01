// Cấu hình chung của dự án Gradle (Gradle = công cụ build, biến mã nguồn thành file APK).
pluginManagement {
    repositories {
        google()            // kho thư viện của Google (Android Gradle Plugin, AndroidX)
        mavenCentral()      // kho thư viện chung (Kotlin, Shizuku...)
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FloatTile"
include(":app")
