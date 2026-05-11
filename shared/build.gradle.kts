plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    // Android
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    // Desktop (JVM) — macOS / Windows / Linux 共享
    jvm("desktop")

    // iOS / macOS Native — 后续如需 SwiftUI，取消注释
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()
    // macosX64()
    // macosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Kotlin Coroutines（异步、Flow）
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                // Android 特定依赖
            }
        }

        val desktopMain by getting {
            dependsOn(commonMain)
            dependencies {
                // Desktop 特定依赖
            }
        }

        // 后续如需原生 macOS/iOS，取消注释
        // val iosMain by creating { dependsOn(commonMain) }
        // val iosX64Main by getting { dependsOn(iosMain) }
        // val iosArm64Main by getting { dependsOn(iosMain) }
        // val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        // val macosX64Main by getting { dependsOn(iosMain) }
        // val macosArm64Main by getting { dependsOn(iosMain) }
    }
}

android {
    namespace = "com.yourapp.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
