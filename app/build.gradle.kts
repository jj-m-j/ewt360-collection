plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.chaquopy)
}

android {
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    namespace = "com.ewt.answer"
    defaultConfig {
        applicationId = "com.ewt.answer"
        minSdk = 24
        targetSdk = 37
        // 构建号 = 当前是第几个成功的 build（workflow 通过 BUILD_NUMBER 传入）
        val buildNumber = System.getenv("BUILD_NUMBER") ?: "1"
        versionCode = buildNumber.toIntOrNull() ?: 1
        versionName = "build ${buildNumber}"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    signingConfigs {
        register("release") {
            enableV3Signing = true
            enableV4Signing = true
        }
    }
    buildTypes {
        release {
            optimization.enable = true
            vcsInfo.include = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    packaging {
        jniLibs {
            excludes += "lib/*/libandroidx.graphics.path.so"
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("httpx")
        }
    }
}

dependencies {
    // MIUIX 组件库
    implementation(libs.miuix.ui)
    // Compose Multiplatform
    implementation(libs.jetbrains.compose.foundation)
    implementation(libs.jetbrains.compose.ui)
    implementation(libs.jetbrains.compose.runtime)
    implementation(libs.jetbrains.compose.animation)
    implementation(libs.jetbrains.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    // 网络层
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // 图片加载
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Wiris 公式图为 SVG，需要 coil-svg 解码器
    implementation(libs.coil.svg)
    // Liquid Glass：AndroidLiquidGlass-kmp（顶栏/底栏模糊），官方库依赖，不再内嵌源码
    implementation("io.github.kyant0:backdrop:2.0.0")
    // shapes：lens 效果需要的圆角矩形形状（官方库）
    implementation("io.github.kyant0:shapes:1.2.0")
    // androidx.annotation（FloatRange/RequiresApi 等）
    implementation("androidx.annotation:annotation:1.9.1")
}
