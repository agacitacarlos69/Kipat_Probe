import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kipat.probe"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kipat.probe"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0-shell"

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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
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
