plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Public OAuth identifier only. Client secrets must NEVER be placed in the APK.
val githubClientId = providers.gradleProperty("forge.githubClientId")
    .orElse(providers.environmentVariable("GITHUB_OAUTH_CLIENT_ID")).getOrElse("").trim()
require(githubClientId.isEmpty() || githubClientId.matches(Regex("[A-Za-z0-9_.-]{1,128}"))) {
    "forge.githubClientId must be a public GitHub OAuth client ID, not a URL or secret."
}
android {
    namespace = "dev.eternalz.forge"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.eternalz.forge"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.3"
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"$githubClientId\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    val signingPath = providers.environmentVariable("FORGE_SIGNING_STORE").orNull
    if (!signingPath.isNullOrBlank()) {
        signingConfigs {
            create("distribution") {
                storeFile = file(signingPath)
                storePassword = providers.environmentVariable("FORGE_SIGNING_PASSWORD").get()
                keyAlias = "forge"
                keyPassword = providers.environmentVariable("FORGE_SIGNING_PASSWORD").get()
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        debug {
            if (!signingPath.isNullOrBlank()) signingConfig = signingConfigs.getByName("distribution")
        }
        release {
            if (!signingPath.isNullOrBlank()) signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Native TextView renderer; no WebView and no remote image loading.
    implementation("io.noties.markwon:core:4.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val verifyGitHubSignInConfiguration = tasks.register("verifyGitHubSignInConfiguration") {
    doLast {
        check(githubClientId.isNotBlank()) {
            "Release sign-in is not configured. Set the PUBLIC GITHUB_OAUTH_CLIENT_ID or -Pforge.githubClientId. Never embed a client secret. See docs/github-sign-in.md."
        }
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(verifyGitHubSignInConfiguration)
}
