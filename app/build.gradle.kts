plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.zhenbang.otw"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhenbang.otw"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.zhenbang.otw"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// app/build.gradle.kts

dependencies {
    // --- Core KTX & Lifecycle / Compose Runtime ---
    implementation(libs.androidx.core.ktx) // Keep ONE core-ktx
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose) // For collectAsStateWithLifecycle etc.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodelCompose) // For viewModel() delegate
    // implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0") // Add to TOML as libs.androidx.lifecycle.viewmodel.ktx if needed separately

    // --- Firebase (using BOM) ---
    implementation(platform(libs.firebase.bom)) // Defines versions for other Firebase libs
    implementation(libs.firebase.auth.ktx)     // Essential for Firebase Auth + Kotlin extensions
    implementation(libs.firebase.analytics)   // Firebase Analytics KTX
    implementation(libs.firebase.functions.ktx) // Firebase Functions KTX (if you use Cloud Functions)

    // --- AppAuth (Google Sign In via OAuth) ---
    implementation(libs.openid.appauth) // Use the libs version (ensure defined in toml)

    // --- AndroidX Security (EncryptedSharedPreferences) ---
    // Ensure libs.androidx.security.crypto points to EITHER 1.0.0 OR 1.1.0-alpha06 in libs.versions.toml
    implementation(libs.androidx.security.crypto)

    // --- AndroidX Browser (CustomTabsIntent) ---
    // Ensure libs.androidx.browser points to the desired version (e.g., 1.8.0) in libs.versions.toml
    implementation(libs.androidx.browser)

    // --- Kotlin Coroutines ---
    // Ensure libs.kotlinx.coroutines.android points to the desired version in libs.versions.toml
    implementation(libs.kotlinx.coroutines.android)

    // --- Compose UI & Navigation ---
    implementation(platform(libs.androidx.compose.bom)) // Compose BOM
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.iconsExtended)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}