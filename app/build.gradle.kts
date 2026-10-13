plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

    buildTypes {
        release {
            optimization {
                enable = false
            }
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

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

// `./gradlew :app:testDebugUnitTest -Pkipat.screenshots=true` writes smoke-test PNGs for review.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("kipat.screenshots").orNull?.let { systemProperty("kipat.screenshots", it) }
    // Optional Maven mirror for Robolectric's android-all download (e.g. when Maven Central rate-limits CI).
    providers.gradleProperty("robolectric.dependency.repo.url").orNull?.let { systemProperty("robolectric.dependency.repo.url", it) }
}
