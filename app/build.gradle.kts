plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
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
        versionCode = 1
        versionName = "1.0.0"
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
    // Liquid Glass：backdrop 已源码集成（app/src/main/kotlin/com/kyant/backdrop）
    // shapes：lens 效果需要的圆角矩形形状（官方库）
    implementation("io.github.kyant0:shapes:1.2.0")
    // androidx.annotation（FloatRange/RequiresApi 等）
    implementation("androidx.annotation:annotation:1.9.1")
}
