// Релизный ключ берётся из файла upload-keystore.jks в корне проекта.
// В CI он восстанавливается из секрета KEYSTORE_BASE64, локально его просто нет —
// тогда release собирается без подписи, а debug работает как обычно.
val uploadKeystore = rootProject.file(System.getenv("KEYSTORE_FILE") ?: "upload-keystore.jks")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.baremodel.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.baremodel.app"
        minSdk = 24
        targetSdk = 35
        // Каждая загрузка в Play требует нового versionCode — CI подставляет номер запуска.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.9.0"
    }

    signingConfigs {
        if (uploadKeystore.exists()) {
            create("release") {
                storeFile = uploadKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
        getByName("debug") {
            // ПОСТОЯННЫЙ отладочный ключ из репозитория: без него CI подписывал бы
            // каждую сборку случайным ключом, и обновление требовало бы удалить
            // старую версию — вместе со всеми проектами пользователя.
            // Это ключ только для бета-раздачи; в Play идёт release-ключ из секрета.
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            // v1 нужна старым эмуляторам и сторонним установщикам, v2/v3 — современным Android
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (uploadKeystore.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("com.google.ar:core:1.45.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.ui.tooling)
}
