plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.blackatsystems.miguardia"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.blackatsystems.miguardia"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "2.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localDebugKeystore = providers.gradleProperty("miguardia.debugKeystore")
        .orNull
        ?.let(rootProject::file)
        ?: rootProject.file(".local-signing/debug.keystore")
    if (localDebugKeystore.isFile) {
        signingConfigs.getByName("debug").storeFile = localDebugKeystore
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += listOf("debug")
        }
    }

    sourceSets.getByName("test").kotlin.srcDir("src/testSupport/java")
    sourceSets.getByName("androidTest").kotlin.srcDir("src/testSupport/java")

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    // AGP 9 creates only Debug unit tests by default; audit Release explicitly.
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        variantBuilder.hostTests.getValue(
            com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE,
        ).enable = true
    }
    beforeVariants(selector().withBuildType("qa")) { variantBuilder ->
        variantBuilder.enableAndroidTest = true
    }
}

// Plain JVM checks must run without another checkout or a JavaScript runtime.
// Select the cross-project contract explicitly with -Pmiguardia.tests=interop.
val jvmTestSelection = providers.gradleProperty("miguardia.tests").orElse("local")
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val selection = jvmTestSelection.get()
    require(selection in setOf("local", "interop")) {
        "miguardia.tests must be local or interop"
    }
    inputs.property("miguardia.tests", selection)
    useJUnit {
        val category = "com.blackatsystems.miguardia.beta.WebCryptoInterop"
        if (selection == "interop") includeCategories(category) else excludeCategories(category)
    }
    if (selection == "interop") {
        val website = providers.gradleProperty("miguardia.website")
            .orElse(providers.environmentVariable("MIGUARDIA_BETA_WEBSITE")).orNull
        val node = providers.gradleProperty("miguardia.node")
            .orElse(providers.environmentVariable("NODE_BINARY")).orNull
        require(!website.isNullOrBlank() && !node.isNullOrBlank()) {
            "Interop requires explicit miguardia.website and miguardia.node paths; use tools/verify.py --profile interop"
        }
        val websiteRoot = rootProject.file(website)
        val nodeExecutable = rootProject.file(node)
        require(websiteRoot.resolve("lib/miguardia-beta/crypto.ts").isFile &&
            websiteRoot.resolve("tests/helpers.mjs").isFile && nodeExecutable.isFile) {
            "Interop requires an existing website checkout and Node executable"
        }
        environment("MIGUARDIA_BETA_WEBSITE", websiteRoot.absolutePath)
        environment("NODE_BINARY", nodeExecutable.absolutePath)
        environment("NODE_OPTIONS", "")
        inputs.files(fileTree(websiteRoot.resolve("lib/miguardia-public")) { include("**/*.ts") },
            websiteRoot.resolve("tests/fixtures/public-distribution/android-oracles.json"),
            websiteRoot.resolve("docs/MIGUARDIA_PUBLIC_DISTRIBUTION_API_V1.md"))
            .withPropertyName("websitePublicContract")
        inputs.files(fileTree(websiteRoot.resolve("lib/miguardia-beta")) { include("**/*.ts") })
            .withPropertyName("websiteBetaContract")
        inputs.files(websiteRoot.resolve("tests/helpers.mjs"), websiteRoot.resolve("package.json"),
            websiteRoot.resolve("package-lock.json"), nodeExecutable)
            .withPropertyName("websiteInteropRuntime")
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    add("qaImplementation", libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
