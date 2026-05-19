plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.androidLint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)
    // Lint host (Studio / lint CLI) already ships kotlin-stdlib at runtime.
    // Keep it compileOnly so the published lint jar is the single artifact
    // expected by AGP's `lintPublish` configuration.
    compileOnly(kotlin("stdlib"))

    testImplementation(libs.junit)
    testImplementation(libs.lint.api)
    testImplementation(libs.lint.checks)
    testImplementation(libs.lint.tests)
}
