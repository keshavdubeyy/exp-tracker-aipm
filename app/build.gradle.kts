import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Backend for the AI categoriser (Feature 1 §3.2). Set in local.properties (never committed):
//   ai.baseUrl=https://your-server.example.com   (http://10.0.2.2:8000 for the emulator)
//   ai.testerKey=…                                (same as TESTER_KEY on the server)
// Empty = AI off: the app still works with rules and learned choices.
val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}
fun localProp(name: String) = "\"" + (localProps.getProperty(name) ?: "").replace("\"", "") + "\""

android {
    namespace = "com.yourteam.expensetracker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.yourteam.expensetracker"
        minSdk = 26
        targetSdk = 37
        // Tester uploads pass -PversionCode=… (scripts/distribute.sh) so every build installs as an update.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
        buildConfigField("String", "AI_BASE_URL", localProp("ai.baseUrl"))
        buildConfigField("String", "AI_TESTER_KEY", localProp("ai.testerKey"))
        // Tester metrics upload (MVP M24). Defaults to the same server as the AI; empty = no upload.
        // In-app "Update available" for testers (Firebase App Distribution, fast build only). From local.properties;
        // empty = no update check. App ID, Web API key and Project ID are in Firebase console → Project settings.
        buildConfigField("String", "FIREBASE_APP_ID", localProp("firebase.appId"))
        buildConfigField("String", "FIREBASE_API_KEY", localProp("firebase.apiKey"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", localProp("firebase.projectId"))
        buildConfigField("String", "METRICS_BASE_URL", "\"" + (localProps.getProperty("metrics.baseUrl") ?: localProps.getProperty("ai.baseUrl") ?: "").replace("\"", "") + "\"")
    }

    buildTypes {
        debug {
            // Short test delays (5 s pop-up reminder, 2 min checks) and the "Sample data" switch.
            buildConfigField("boolean", "TEST_BUILD", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "TEST_BUILD", "false")
        }
        // Same test behaviour as debug, but optimised like a real release, so it runs smoothly.
        // Debug builds are much slower at drawing Compose screens; use this to judge smoothness.
        create("fast") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("boolean", "TEST_BUILD", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen) // system splash (ONBOARDING.md)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    // Scan & Pay (M7): Google's code scanner. Runs in Play services, needs no camera permission.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Tester update prompt: the API is a no-op stub in every build; the full SDK (which can install updates)
    // goes only into the "fast" build sent through Firebase, never into a release.
    implementation(libs.firebase.appdistribution.api)
    add("fastImplementation", libs.firebase.appdistribution)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
