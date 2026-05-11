plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("io.ktor:ktor-client-core:2.3.7")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
                implementation("io.ktor:ktor-client-logging:2.3.7")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:2.3.7")
            }
        }

        val desktopMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-java:2.3.7")
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
