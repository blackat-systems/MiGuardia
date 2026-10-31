plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

abstract class GenerateStage12Pins : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val runnerEnabled: org.gradle.api.provider.Property<Boolean>
    @get:org.gradle.api.tasks.Input
    abstract val qaSha256: org.gradle.api.provider.Property<String>
    @get:org.gradle.api.tasks.OutputDirectory
    abstract val generatedDirectory: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val admitted = runnerEnabled.get()
        val hash = qaSha256.get()
        require(!admitted || hash.matches(Regex("[0-9a-f]{64}"))) {
            "Stage12 requires the exact QA APK SHA-256 before compiling its runner"
        }
        val output = generatedDirectory.file("com/blackatsystems/miguardia/stage12/Stage12BuildPins.kt").get().asFile
        check(output.parentFile.isDirectory || output.parentFile.mkdirs())
        output.writeText("""
            package com.blackatsystems.miguardia.stage12
            internal object Stage12BuildPins {
                const val ENABLED: Boolean = $admitted
                const val QA_SHA256: String = "$hash"
            }
        """.trimIndent() + "\n")
    }
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

        // Explicit test-only opt-in; normal app/test defaults remain unchanged.
        val stage10Runner = providers.gradleProperty("miguardia.stage10Runner").orNull
        val stage11Runner = providers.gradleProperty("miguardia.stage11Runner").orNull
        val stage12Runner = providers.gradleProperty("miguardia.stage12Runner").orNull
        val stage12NormalRunner = providers.gradleProperty("miguardia.stage12NormalRunner").orNull
        require(stage10Runner == null || stage10Runner == "true")
        require(stage11Runner == null || stage11Runner == "true")
        require(stage12Runner == null || stage12Runner == "true")
        require(stage12NormalRunner == null || stage12NormalRunner == "true")
        require(stage12Runner != "true" || providers.gradleProperty("miguardia.stage12Harness").orNull == "true") {
            "Stage12 runner requires its explicit QA bootstrap harness"
        }
        require(listOf(stage10Runner, stage11Runner, stage12Runner, stage12NormalRunner).count { it == "true" } <= 1)
        require(stage12NormalRunner != "true" || providers.gradleProperty("miguardia.stage12Harness").orNull != "true") {
            "Normal Stage12 QA uses the ordinary QA Application, not the neutral harness"
        }
        testInstrumentationRunner = if (stage10Runner == "true")
            "com.blackatsystems.miguardia.upgrade.Stage10UpgradeRunner"
        else if (stage11Runner == "true")
            "com.blackatsystems.miguardia.backup.Stage11RestoreRunner"
        else if (stage12Runner == "true")
            "com.blackatsystems.miguardia.stage12.Stage12Runner"
        else if (stage12NormalRunner == "true")
            "com.blackatsystems.miguardia.stage12.Stage12NormalQaRunner"
        else "androidx.test.runner.AndroidJUnitRunner"
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
            val stage12Harness = providers.gradleProperty("miguardia.stage12Harness").orNull
            require(stage12Harness == null || stage12Harness == "true")
            manifestPlaceholders["stage12BootstrapProvidersEnabled"] = stage12Harness != "true"
            manifestPlaceholders["stage12HarnessEnabled"] = stage12Harness == "true"
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

// Pins live only in the test APK. Changing a run UUID or round never rebuilds QA.
val generateStage12Pins = tasks.register<GenerateStage12Pins>("generateStage12Pins") {
    runnerEnabled.set(providers.provider {
        providers.gradleProperty("miguardia.stage12Runner").orNull == "true" ||
            providers.gradleProperty("miguardia.stage12NormalRunner").orNull == "true"
    })
    qaSha256.set(providers.gradleProperty("miguardia.stage12QaSha256").orElse(""))
    generatedDirectory.set(layout.buildDirectory.dir("generated/stage12Pins"))
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
    onVariants { variant ->
        variant.androidTest?.sources?.kotlin?.addGeneratedSourceDirectory(generateStage12Pins) {
            it.generatedDirectory
        }
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
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
