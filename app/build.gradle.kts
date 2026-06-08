import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val admobAppId = System.getenv("ADMOB_APP_ID") ?: localProperties.getProperty("admob.app.id") ?: ""
val admobBannerId = System.getenv("ADMOB_BANNER_ID") ?: localProperties.getProperty("admob.banner.id") ?: ""
val admobInterstitialId = System.getenv("ADMOB_INTERSTITIAL_ID") ?: localProperties.getProperty("admob.interstitial.id") ?: ""
val admobRewardId = System.getenv("ADMOB_REWARD_ID") ?: localProperties.getProperty("admob.reward.id") ?: ""

val githubToken = System.getenv("GH_API_TOKEN") ?: localProperties.getProperty("github.api.token") ?: ""
val githubOwner = System.getenv("GH_REPO_OWNER") ?: localProperties.getProperty("github.repo.owner") ?: ""
val githubName = System.getenv("GH_REPO_NAME") ?: localProperties.getProperty("github.repo.name") ?: ""

// Release signing. On CI these come from the environment (the keystore is decoded
// from the KEYSTORE_BASE64 secret); locally they can be set in local.properties.
val keystoreFilePath = System.getenv("KEYSTORE_FILE") ?: localProperties.getProperty("keystore.file")
val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProperties.getProperty("keystore.password")
val keystoreKeyAlias = System.getenv("KEY_ALIAS") ?: localProperties.getProperty("key.alias")
val keystoreKeyPassword = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("key.password")
val hasReleaseKeystore = keystoreFilePath != null && file(keystoreFilePath).exists()

// versionCode is bumped on every CI build (set from the workflow run number via the
// VERSION_CODE env var); both fall back to sensible defaults for local builds.
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val ciVersionName = System.getenv("VERSION_NAME") ?: "0.1.0"

android {
    namespace = "com.charles.trailsage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.charles.trailsage"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        buildConfigField("String", "ADMOB_REWARD_ID", "\"$admobRewardId\"")
        buildConfigField("String", "GITHUB_API_TOKEN", "\"$githubToken\"")
        buildConfigField("String", "GITHUB_REPO_OWNER", "\"$githubOwner\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"$githubName\"")
    }
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreFilePath!!)
                storePassword = keystorePassword
                keyAlias = keystoreKeyAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign with the release key when it is available (CI / configured local builds);
            // otherwise the APK is left unsigned so debug builds still work without the key.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        // LiteRT-LM (litertlm-android) ships Java 21 bytecode; the toolchain must read/emit JVM 21.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// LiteRT-LM ships Java 21 bytecode; pin the whole Kotlin/kapt toolchain to JDK 21 so the
// kapt stub javac reads it (otherwise it builds against a Java 17 system image and fails).
kotlin {
    jvmToolchain(21)
}

// The JDK-21 toolchain runs kapt in a separate Gradle worker daemon that is launched with
// no TEMP env, so Room's sqlite-jdbc verifier tries to extract its native lib into a
// non-writable system dir (C:\WINDOWS) and fails. Point that worker at a writable build dir.
// Portable: derived from the build directory, harmless on CI/Linux.
tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask>().configureEach {
    val kaptTmp = layout.buildDirectory.dir("kapt-tmp").get().asFile.apply { mkdirs() }.absolutePath
    kaptProcessJvmArgs.addAll(
        "-Djava.io.tmpdir=$kaptTmp",
        "-Dorg.sqlite.tmpdir=$kaptTmp",
    )
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    implementation("com.google.dagger:hilt-android:2.56.2")
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.12.0")
    implementation(files("libs/sherpa-onnx-1.13.2.aar"))
    implementation("org.apache.commons:commons-compress:1.27.1")
    kapt("androidx.room:room-compiler:2.7.1")
    kapt("com.google.dagger:hilt-compiler:2.56.2")
    kapt("androidx.hilt:hilt-compiler:1.2.0")
    implementation(platform("com.google.firebase:firebase-bom:33.14.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.google.android.gms:play-services-ads:23.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

kapt { correctErrorTypes = true }

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    apply(plugin = "com.google.firebase.firebase-perf")
}
