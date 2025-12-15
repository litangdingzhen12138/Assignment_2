plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
        namespace = "com.example.assignment_2"
        compileSdk = 35

    defaultConfig {
        applicationId = "com.example.assignment_2"
        minSdk = 26
        targetSdk = 35
    }

    // 防止 aapt 压缩 mp4，避免某些机型 Asset 读取异常（可选但推荐）
    androidResources {
        noCompress += listOf("mp4")
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
//    val media3Version = "1.8.0" // stable 2025-12-01 :contentReference[oaicite:5]{index=5}
//
//    implementation("androidx.media3:media3-exoplayer:$media3Version")
//    implementation("androidx.media3:media3-ui:$media3Version")
//
//    // 协程
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
//
//    // Vosk (离线语音识别) :contentReference[oaicite:6]{index=6}
//    implementation("com.alphacephei:vosk-android:0.3.75")

    // JSON（Android 自带 org.json 也行；这里不用额外依赖也可以）
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")

    implementation("com.alphacephei:vosk-android:0.3.75")
}
