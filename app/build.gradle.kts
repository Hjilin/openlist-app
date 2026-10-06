import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ============================================================
// versionCode 自动递增（用户偏好：每次构建自动 +1）
// 版本号存储在根目录 version.properties
// ============================================================
val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) {
        FileInputStream(versionFile).use { load(it) }
    } else {
        setProperty("versionCode", "1")
        setProperty("versionName", "0.1.0")
    }
}
val buildVersionCode = versionProps.getProperty("versionCode", "1").toInt()
val buildVersionName = versionProps.getProperty("versionName", "0.1.0")
val nextVersionCode = buildVersionCode + 1

android {
    namespace = "com.pocketnas.pro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pocketnas.pro"
        minSdk = 26
        targetSdk = 34
        versionCode = buildVersionCode
        versionName = buildVersionName
        resValue("string", "app_version", buildVersionName)
        ndk {
            // PocketNAS-Pro 内核为 arm64 静态 ELF，声明 ABI 约束
            abiFilters += listOf("arm64-v8a")
        }
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // 内核二进制 libopenlist.so 需要以真实文件解压到 nativeLibraryDir
            // （useLegacyPackaging=true），app 才能直接 exec 启动 OpenList 内核
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Media3 ExoPlayer：完整媒体格式支持（MKV / H.265 / FLAC / OGG / Opus 等系统 MediaPlayer 不支持格式）
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // 后台保活：WorkManager 周期守护
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}

// ============================================================
// 构建完成后自动递增 versionCode 并写回 version.properties
// ============================================================
tasks.register("bumpVersionCode") {
    doLast {
        versionProps.setProperty("versionCode", nextVersionCode.toString())
        versionFile.outputStream().use { versionProps.store(it, "PocketNAS-Pro build version (auto-incremented)") }
        println("==> versionCode bumped: $buildVersionCode -> $nextVersionCode")
    }
}
tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
    finalizedBy("bumpVersionCode")
}
