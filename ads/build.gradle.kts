plugins {
    alias(libs.plugins.androidLibrary)
    // Compose enabled here vì module export Composable BannerAd (giống cách :app bật Compose).
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "com.tohsoft.ads"
    compileSdk = 36

    defaultConfig {
        // minSdk 21 để khớp các module library khác (portability contract trong CLAUDE.md).
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(project(":app-events"))

    // Bridge AdMob callbacks → analytics. :ads là module duy nhất biết SDK ads
    // cụ thể (AdMob), nên giữ glue ở đây thay vì :app (xem app-event/docs/ADS_EVENT_GUIDE.md).
    // AdsEventTracker (SDK-agnostic) sống ở :app-event; AdRevenueLike ở :firebase-events.
    implementation(project(":firebase-events"))

    // Google Mobile Ads (AdMob). `api` để host app dùng trực tiếp AdRequest/MobileAds nếu cần.
    api(libs.google.ads)
}
