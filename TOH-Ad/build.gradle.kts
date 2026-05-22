plugins {
    alias(libs.plugins.androidLibrary)
    // Compose enabled vì module export Composable BannerAd (analytics bridge), giống :ads cũ.
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "com.tohsoft.ad"
    compileSdk = 36

    defaultConfig {
        // minSdk 23 để khớp :app demo (TOH-Ad gốc dùng 24; hạ xuống tránh manifest-merger conflict).
        minSdk = 23
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.gson)
    // AdsModule khởi tạo MobileAds off-main-thread bằng coroutines.
    implementation(libs.kotlinx.coroutines.android)

    // Compose: chỉ cho analytics bridge BannerAd (com.tohsoft.ads.analytics).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Bridge ad callbacks → analytics. :TOH-Ad là module duy nhất biết SDK ads cụ thể.
    // AdsEventTracker (SDK-agnostic) sống ở :app-events; AdRevenueLike/AdResult ở :firebase-events.
    implementation(project(":app-events"))
    implementation(project(":firebase-events"))

    // Google Mobile Ads (AdMob) + UMP. `api` để host app dùng trực tiếp nếu cần.
    api(libs.google.ads)
    api(libs.google.ump)
}
