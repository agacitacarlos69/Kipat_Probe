import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Build identity. CI passes these (pilot-apk.yml); local builds fall back to git and "local".
// The pilot version code (100 + build number) stays above the v0.2.0 release (2), so a pilot APK
// signed with the same key installs over it as an update. Future store releases must use >= 10000.
val buildNumber: Int = providers.environmentVariable("KIPAT_BUILD_NUMBER").orNull?.toIntOrNull() ?: 0
val gitSha: String = providers.environmentVariable("KIPAT_GIT_SHA").orNull?.take(12)
    ?: runCatching {
        providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }.standardOutput.asText.get().trim()
    }.getOrDefault("unknown")
val buildTime: String = providers.environmentVariable("KIPAT_BUILD_TIME").orNull ?: "local build"

android {
    namespace = "com.kipat.probe"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kipat.probe"
        minSdk = 26
        targetSdk = 37
        versionCode = if (buildNumber > 0) 100 + buildNumber else 2
        versionName = if (buildNumber > 0) "0.2.1" else "0.2.0-shell"

        // Build identity, shown in Developer tools so two phones can prove they run the same build.
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from a gitignored keystore.properties at the repo root, or from
    // KIPAT_KEYSTORE_* env vars in CI. Without either, assembleRelease produces an unsigned APK.
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(prop: String, env: String): String? =
        keystoreProps.getProperty(prop) ?: providers.environmentVariable(env).orNull
    val releaseStoreFile = signingValue("storeFile", "KIPAT_KEYSTORE_FILE")

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "KIPAT_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KIPAT_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KIPAT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        // Physical pilot builds (CI: .github/workflows/pilot-apk.yml). Same application id and the
        // same stable release key, so each new pilot APK installs as an UPDATE (Room database,
        // profile and settings survive). Non-debuggable: no sample data, real-user behaviour.
        // Never signed with a throwaway key: without the release key the APK stays unsigned.
        create("pilot") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-pilot.$buildNumber"
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Robolectric smoke tests need merged resources and the merged manifest.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)
    // QR encoding for the Share screen (pure Java, no UI).
    implementation(libs.zxing.core)
    // Local-first persistence for Connections and recent taps (works offline, survives restarts).
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

// Room schema history (committed) so future migrations can be written and tested.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// `./gradlew :app:testDebugUnitTest -Pkipat.screenshots=true` writes smoke-test PNGs for review.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("kipat.screenshots").orNull?.let { systemProperty("kipat.screenshots", it) }
    // Optional Maven mirror for Robolectric's android-all download (e.g. when Maven Central rate-limits CI).
    providers.gradleProperty("robolectric.dependency.repo.url").orNull?.let { systemProperty("robolectric.dependency.repo.url", it) }
}
