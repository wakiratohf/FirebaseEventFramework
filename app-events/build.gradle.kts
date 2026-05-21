plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.tohsoft.app_event"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Coroutines — debounce + flush in AppExitTracker.
    implementation(libs.kotlinx.coroutines.android)
    // ProcessLifecycleOwner — used by AppEventsInstaller (high-level drop-in).
    // Tracker classes do not import lifecycle; only the installer does.
    implementation(libs.androidx.lifecycle.process)
    // Depends on firebase-events for the underlying log call. App-level helpers
    // live here; pure Firebase logging logic stays in :firebase-events.
    implementation(project(":firebase-events"))

    testImplementation(libs.junit)
}
