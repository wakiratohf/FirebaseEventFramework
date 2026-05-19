plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.tohsoft.firebase_events"
    compileSdk = 35

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
    // Stub-out Android framework calls (e.g. android.util.Log) in unit tests
    // so they return defaults instead of throwing "not mocked" exceptions.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.gson)
    implementation(libs.material)
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    // No project(":...") dependencies — this SDK is self-contained so it can
    // be copy-pasted into other Android projects. See firebase-events/README.md
    // for the adapter pattern used to inject AdRevenue/Webhook implementations.

    // Test (JVM only — these targets don't touch Bundle/Android runtime).
    testImplementation(libs.junit)
    testImplementation(libs.gson)
}
