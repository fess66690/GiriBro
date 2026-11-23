import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

android {
    namespace = "ru.petproject.giribro"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.petproject.giribro"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.3.1"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                val storePath = keystoreProperties.getProperty("storeFile")
                val storePasswordValue = keystoreProperties.getProperty("storePassword")
                val alias = keystoreProperties.getProperty("keyAlias")
                val keyPasswordValue = keystoreProperties.getProperty("keyPassword")
                require(!storePath.isNullOrBlank()) { "keystore.properties: storeFile is required" }
                require(!storePasswordValue.isNullOrBlank()) { "keystore.properties: storePassword is required" }
                require(!alias.isNullOrBlank()) { "keystore.properties: keyAlias is required" }
                require(!keyPasswordValue.isNullOrBlank()) { "keystore.properties: keyPassword is required" }

                storeFile = rootProject.file(storePath)
                storePassword = storePasswordValue
                keyAlias = alias
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            // Для отладки на эмуляторе (x86_64) и на реальных ARM-устройствах.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
        release {
            // MediaPipe поставляет нативные библиотеки под каждую ABI.
            // В RuStore уходит универсальный APK только для ARM-устройств.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    val cameraXVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("androidx.camera:camera-video:$cameraXVersion")

    implementation("com.google.mediapipe:tasks-vision:0.10.35")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Реальная реализация org.json для JVM-тестов сериализации.
    testImplementation("org.json:json:20231013")
}

kapt {
    correctErrorTypes = true
}
