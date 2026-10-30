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
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Robolectric downloads its Android runtime in the test JVM.
            listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort",
                "http.nonProxyHosts").forEach { key ->
                System.getProperty(key)?.let { test.systemProperty(key, it) }
            }
            test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    testImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.material:material:1.6.8")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.leanback:leanback:1.0.0")
    // Leanback 1.0 brings Fragment 1.0, which predates ActivityResult support.
    implementation("androidx.fragment:fragment:1.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
}
