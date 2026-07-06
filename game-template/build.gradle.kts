/**
 * Game APK 构建脚本 (模板)
 *
 * 使用说明:
 *   1. 替换 namespace 和 applicationId 为你的包名
 *   2. 将游戏文件放入 app/src/main/assets/game/
 *   3. 与 Runtime APK 使用同一证书签名
 *   4. 运行 ./gradlew :app:assembleRelease
 */

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.yourapp.game1"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yourapp.game1"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // 不对 assets/game 下的文件进行 AAPT 压缩
    // (native ELF/PE 文件需要 mmap 对齐, JSON 需要直接读取)
    aaptOptions {
        noCompress += "assets/game/"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.0")
}
