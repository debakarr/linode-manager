import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

ktlint {
    // Report rather than fail the build for style nits; CI surfaces the list.
    ignoreFailures = false
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

/**
 * Build inputs that differ between a laptop and CI.
 *
 * Locally they come from `local.properties`; in CI the release workflow passes
 * `-PversionName=… -PversionCode=…` from the git tag and supplies the keystore
 * as secrets. Nothing here is committed — see .github/workflows/release.yml.
 */
fun buildInput(
    property: String,
    env: String = property,
): String? = (findProperty(property) as String?) ?: localProperties.getProperty(property) ?: System.getenv(env)

val appVersionName: String = buildInput("versionName") ?: "2.0.5"
val appVersionCode: Int = buildInput("versionCode")?.toIntOrNull() ?: 18

android {
    namespace = "com.linode.manager"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.linode.manager"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            // The keystore itself is never committed. Locally it is referenced
            // from local.properties; in CI the workflow writes it from a secret.
            val storePath =
                buildInput("keystore.path", "KEYSTORE_PATH")
                    ?: localProperties.getProperty("keystore.path")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = buildInput("keystore.password", "KEYSTORE_PASSWORD")
                    ?: error("keystore.path is set but keystore.password is missing (local.properties or KEYSTORE_PASSWORD)")
                keyAlias = buildInput("keystore.alias", "KEYSTORE_ALIAS") ?: "linode"
                keyPassword = buildInput("keyPassword", "KEY_PASSWORD")
                    ?: buildInput("keystore.password", "KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (buildInput("keystore.path", "KEYSTORE_PATH") != null ||
                    localProperties.getProperty("keystore.path") != null
                ) {
                    signingConfigs.getByName("release")
                } else {
                    null
                }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.security.crypto)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    // sshlib depends on desktop Tink; security-crypto on tink-android. Same
    // classes — use the Android flavor once, at the version sshlib expects.
    implementation(libs.sshlib) {
        exclude(group = "com.google.crypto.tink", module = "tink")
    }
    implementation(libs.tink.android)
    implementation(libs.termlib)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    // JVM screenshot tests of every screen against a mock API (no device needed).
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.okhttp.mockwebserver)
    // Android's JCE provider, to reproduce device crypto behaviour on the JVM.
    testImplementation("org.conscrypt:conscrypt-openjdk-uber:2.6.2")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// The Compose mapping tasks only produce diagnostics for R8 stack traces and
// choke on multi-release jars in the dependency graph; R8 itself is fine.
tasks.configureEach {
    if (name.contains("ReleaseComposeMapping")) enabled = false
}

// CI containers often have tiny entropy pools; SecureRandom (used by JSch key
// exchange) would block on /dev/random. Non-blocking urandom is fine for tests.
tasks.withType<Test> {
    systemProperty("java.security.egd", "file:/dev/./urandom")
    // Roborazzi: write screenshots (build/screenshots) on every run.
    systemProperty("roborazzi.test.record", "true")
    // One JVM per test class: Robolectric installs JCE providers globally,
    // which must not leak into the plain-JVM SSH tests (and vice versa).
    forkEvery = 1
}

// Version-numbered APK filenames for release uploads.
val releaseVersion = appVersionName
tasks.register<Copy>("versionApks") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    into(layout.buildDirectory.dir("outputs/apk/versioned"))
    rename { name ->
        name
            .replace("app-arm64-v8a-release.apk", "LinodeManager-$releaseVersion-arm64-v8a.apk")
            .replace("app-armeabi-v7a-release.apk", "LinodeManager-$releaseVersion-armeabi-v7a.apk")
            .replace("app-x86_64-release.apk", "LinodeManager-$releaseVersion-x86_64.apk")
            .replace("app-x86-release.apk", "LinodeManager-$releaseVersion-x86.apk")
            .replace("app-universal-release.apk", "LinodeManager-$releaseVersion-universal.apk")
    }
}
