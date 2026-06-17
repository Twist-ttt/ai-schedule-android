plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.qiu.aischedule"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.qiu.aischedule"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 界面基础
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.constraintlayout)
    implementation(libs.cardview)

    // Lifecycle: LiveData / ViewModel（用于列表自动刷新）
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel)

    // Room 本地数据库
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // 网络：OkHttp + Gson（调用 DeepSeek LLM 接口）
    implementation(libs.okhttp)
    implementation(libs.gson)

    // API Key 加密存储
    implementation(libs.security.crypto)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
