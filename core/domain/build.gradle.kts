plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.blackatsystems.miguardia.core.domain"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
}
