plugins {
    alias(libs.plugins.android.application)
    id("com.chaquo.python")
}

configurations.all {
    resolutionStrategy {
        // Force consistent test core version
        force("androidx.test:core:1.6.1")
        force("androidx.test:runner:1.6.1")
        force("androidx.test:rules:1.6.1")
    }
}

android {
    namespace = "io.finett.droidclaw"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.finett.droidclaw"
        minSdk = 22
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        

        // Chaquopy configuration - ABI filters
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
        
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"

        pip {
            // Common packages installed at build time
            // Note: lxml removed due to native dependency requirements
            install("requests")
            install("beautifulsoup4")
            install("python-dateutil")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.recyclerview)
    implementation(libs.okhttp)
    implementation(libs.gson)

    // WorkManager for background task scheduling (heartbeat & cron jobs)
    implementation("androidx.work:work-runtime:2.9.0")
    testImplementation("androidx.work:work-testing:2.9.0")

    // Markwon for markdown rendering
    implementation(libs.markwon.core) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.markwon.ext.strikethrough) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.markwon.ext.tables) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.markwon.ext.tasklist) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.6.1")
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.navigation.testing)
    debugImplementation(libs.fragment.testing)
    androidTestImplementation(libs.fragment.testing)

    // Test orchestrator for parallel test execution
    androidTestUtil("androidx.test:orchestrator:1.5.1")
}