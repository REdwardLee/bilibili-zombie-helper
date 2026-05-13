plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

android {
    namespace = "com.yourapp.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yourapp.android"
        minSdk = 24
        targetSdk = 34
        versionCode = file("version.txt").let { f ->
            if (f.exists()) f.readText().trim().toIntOrNull() ?: 1 else 1
        }
        versionName = "1.0.${versionCode}"
    }

    // 自动递增版本号：每次打包前 version.txt +1
    tasks.register<Exec>("incrementVersion") {
        commandLine("sh", "-c", "echo $(( $(cat version.txt 2>/dev/null || echo 0) + 1 )) > version.txt")
        workingDir = file(".")
    }
    tasks.named("preBuild") { dependsOn("incrementVersion") }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":shared"))

    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core:1.12.0")

    // ViewModel + Coroutines + Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON serialization (for saving zombie data)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
