/*
 * Copyright (c) 2020 GitLive Ltd.  Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

version = project.property("firebase-installations.version") as String

plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdk = property("targetSdkVersion") as Int
    defaultConfig {
        minSdk = property("minSdkVersion") as Int
        targetSdk = property("targetSdkVersion") as Int
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
        }
    }
    testOptions {
        unitTests.apply {
            isIncludeAndroidResources = true
        }
    }
    packagingOptions {
        resources.pickFirsts.add("META-INF/kotlinx-serialization-core.kotlin_module")
        resources.pickFirsts.add("META-INF/AL2.0")
        resources.pickFirsts.add("META-INF/LGPL2.1")
    }
    lint {
        abortOnError = false
    }
}

val KonanTarget.darwinArchitectureVariants: List<String> get() =
    when (this) {
        is KonanTarget.IOS_X64,
        is KonanTarget.IOS_SIMULATOR_ARM64 -> listOf("ios-arm64_x86_64-simulator", "ios-arm64_i386_x86_64-simulator")
        is KonanTarget.IOS_ARM64 -> listOf("ios-arm64", "ios-arm64_arm7")
        is KonanTarget.TVOS_X64,
        is KonanTarget.TVOS_SIMULATOR_ARM64 -> listOf("tvos-arm64_x86_64-simulator")
        is KonanTarget.TVOS_ARM64 -> listOf("tvos-arm64")
        is KonanTarget.MACOS_X64,
        is KonanTarget.MACOS_ARM64 -> listOf("macos-arm64_x86_64")
        else -> listOf()
    }

kotlin {

    android {
        publishAllLibraryVariants()
    }

    val supportIosTarget = project.property("skipIosTarget") != "true"
    val supportTvosTarget = project.property("skipTvosTarget") != "true"
    val supportMacosTarget = project.property("skipMacosTarget") != "true"

    fun darwinTargetConfig(): KotlinNativeTarget.() -> Unit = {
        val nativeFrameworkPaths = listOf(
            "FBLPromises",
            "FirebaseAnalytics",
            "FirebaseAnalyticsSwift",
            "FirebaseCore",
            "FirebaseCoreInternal",
            "FirebaseInstallations",
            "GoogleAppMeasurement",
            "GoogleAppMeasurementIdentitySupport",
            "GoogleUtilities",
            "nanopb"
        ).map { framework ->
            konanTarget.darwinArchitectureVariants
                .map { rootProject.project("firebase-app").projectDir.resolve("src/nativeInterop/cinterop/Carthage/Build/$framework.xcframework/$it") }
                .firstOrNull { it.exists() }
        }

        binaries {
            getTest("DEBUG").apply {
                linkerOpts(nativeFrameworkPaths.map { "-F$it" })
                linkerOpts("-ObjC")
            }
        }

        compilations.getByName("main") {
            cinterops.create("FirebaseInstallations") {
                compilerOpts(nativeFrameworkPaths.map { "-F$it" })
                extraOpts = listOf("-compiler-option", "-DNS_FORMAT_ARGUMENT(A)=", "-verbose")
            }
        }
    }

    if (supportIosTarget) {
        ios(configure = darwinTargetConfig())
        iosSimulatorArm64("ios", configure = darwinTargetConfig())
    }

    if (supportTvosTarget) {
        tvos(configure = darwinTargetConfig())
        tvosSimulatorArm64("tvos", configure = darwinTargetConfig())
    }

    if (supportMacosTarget) {
        macosX64(configure = darwinTargetConfig())
        macosArm64(configure = darwinTargetConfig())
    }

    js {
        useCommonJs()
        nodejs {
            testTask {
                useMocha {
                    timeout = "5s"
                }
            }
        }
        browser {
            testTask {
                useMocha {
                    timeout = "5s"
                }
            }
        }
    }

    sourceSets {
        all {
            languageSettings.apply {
                apiVersion = "1.6"
                languageVersion = "1.6"
                progressiveMode = true
            }
        }

        val commonMain by getting {
            dependencies {
                api(project(":firebase-app"))
                implementation(project(":firebase-common"))
            }
        }

        val androidMain by getting {
            dependencies {
                api("com.google.firebase:firebase-installations")
            }
        }

        val darwinMain by creating {
            dependsOn(commonMain)
        }

        val darwinTest by creating {
            dependsOn(commonMain)
        }

        if (supportIosTarget) {
            val iosMain by getting { dependsOn(darwinMain) }
            val iosTest by sourceSets.getting { dependsOn(darwinTest) }
        }

        if (supportTvosTarget) {
            val tvosMain by getting { dependsOn(darwinMain) }
            val tvosTest by sourceSets.getting { dependsOn(darwinTest) }
        }

        if (supportMacosTarget) {
            val macosX64Main by getting { dependsOn(darwinMain) }
            val macosX64Test by sourceSets.getting { dependsOn(darwinTest) }
            val macosArm64Main by getting { dependsOn(darwinMain) }
            val macosArm64Test by sourceSets.getting { dependsOn(darwinTest) }
        }

        val jsMain by getting
    }
}

if (project.property("firebase-installations.skipIosTests") == "true") {
    tasks.forEach {
        if (it.name.contains("ios", true) && it.name.contains("test", true)) { it.enabled = false }
    }
}

if (project.property("firebase-installations.skipTvosTests") == "true") {
    tasks.forEach {
        if (it.name.contains("ios", true) && it.name.contains("test", true)) { it.enabled = false }
    }
}

if (project.property("firebase-installations.skipMacosTests") == "true") {
    tasks.forEach {
        if (it.name.contains("ios", true) && it.name.contains("test", true)) { it.enabled = false }
    }
}

//signing {
//    val signingKey: String? by project
//    val signingPassword: String? by project
//    useInMemoryPgpKeys(signingKey, signingPassword)
//    sign(publishing.publications)
//}
