plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.silero"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.example.silero"
        minSdk = 24
        targetSdk = 34
        // 版本号遵循语义化版本 SemVer：versionName = "主.次.修订"。
        // 每次发布必须把 versionCode +1（系统据此判断升级），versionName 同步更新：
        //   修 bug  -> 1.0.1（versionCode 2）
        //   加功能  -> 1.1.0（versionCode 3）
        //   大变更  -> 2.0.0
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            // SoundTouch JNI 编译的目标 ABI
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    signingConfigs {
        // 注意：仅用于开源测试，keystore 与密码随仓库公开。
        // 正式商用项目不应把签名信息写进仓库。
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "123456"
            keyAlias = "repeater"
            keyPassword = "123456"
        }
    }

    buildTypes {
        release {
            // 关闭混淆：避免 R8 误删 JNI/native 方法，对小工具体积收益也不大
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Silero VAD 推理
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")
}
