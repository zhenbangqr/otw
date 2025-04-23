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

dependencies {
    // --- Core KTX & Lifecycle / Compose Runtime ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose) // For collectAsStateWithLifecycle etc.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodelCompose) // For viewModel() delegate

    // --- Firebase (using BOM) ---
    implementation(platform(libs.firebase.bom)) // Defines versions for other Firebase libs
    implementation(libs.firebase.auth.ktx)     // Essential for Firebase Auth + Kotlin extensions
    implementation(libs.firebase.analytics)   // Firebase Analytics KTX
    implementation(libs.firebase.functions.ktx) // Firebase Functions KTX (if you use Cloud Functions)

    // --- AppAuth (Google Sign In via OAuth) ---
    implementation(libs.openid.appauth) // Use the libs version (ensure defined in toml)
    // implementation("net.openid:appauth:0.11.1") // REMOVE this duplicate string version

    // --- AndroidX Security (EncryptedSharedPreferences) ---
    // Choose ONE version - EITHER the stable or the alpha, define it in libs.versions.toml
    implementation(libs.androidx.security.crypto) // Use the libs version (ensure defined in toml, pointing to 1.0.0 or 1.1.0-alpha06)
    // implementation("androidx.security:security-crypto:1.0.0") // REMOVE this duplicate string version
    // implementation("androidx.security:security-crypto:1.1.0-alpha06") // REMOVE these duplicate string versions

    // --- AndroidX Browser (CustomTabsIntent) ---
    implementation(libs.androidx.browser) // Use the libs version (ensure defined in toml)
    // implementation("androidx.browser:browser:1.8.0") // REMOVE this duplicate string version

    // --- Kotlin Coroutines ---
    implementation(libs.kotlinx.coroutines.android) // Use the libs version (ensure defined in toml)
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // REMOVE this duplicate string version

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

    // --- Other Dependencies You Had (Check if still needed/covered) ---
    // implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0") // Often useful, add to libs.versions.toml if needed as 'libs.androidx.lifecycle.viewmodel.ktx'

}