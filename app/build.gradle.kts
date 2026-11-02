plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mytvlauncher.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mytvlauncher.app"
        minSdk = 26 // Android TV / Google TV devices (incl. OnePlus TV) run API 26+
        targetSdk = 34
        // CI passes its run number, so every Release build has a higher
        // versionCode (Android won't install an "update" with a lower one)
        // and Settings › About shows which build is on the TV.
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = runNumber
        versionName = "1.$runNumber"
        // TMDB key for the "Popular on Netflix / Prime / …" rows. The
        // TMDB_API_KEY GitHub Actions secret wins if set; otherwise the
        // tmdbApiKey value in gradle.properties. It ships inside the public
        // APK either way — fine for a free read-only TMDB key.
        val tmdbKey = System.getenv("TMDB_API_KEY")?.takeIf { it.isNotBlank() }
            ?: (project.findProperty("tmdbApiKey") as String?) ?: ""
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbKey\"")
    }

    signingConfigs {
        // A fixed debug key committed to the repo. Without it every CI run
        // generated a fresh random debug key, and Android refuses to install
        // an APK over one signed with a different key ("App not installed").
        // Personal sideload-only app, so a committed debug key is fine.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Screenshot tour (app/src/test/.../ScreenshotTour.kt): Robolectric +
    // Roborazzi render real screens on the JVM; CI publishes the PNGs.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "3g"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.material:material:1.6.8")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.animation:animation:1.6.8")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.20.0")
}
