plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "top.ethan2048.easyllm.mcp"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.mcp.sdk)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
