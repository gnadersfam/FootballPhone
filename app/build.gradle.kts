plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub gives every build a number; it becomes the app's version.
val runNumber: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
    namespace = "com.lounge.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lounge.phone"
        minSdk = 24
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // Same idea as the Firestick app: a fixed key kept in the repo so each new build installs over the old one.
    signingConfigs {
        create("lounge") {
            storeFile = file("lounge.jks")
            storePassword = "lounge-open"
            keyAlias = "lounge"
            keyPassword = "lounge-open"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("lounge") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("lounge")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core:1.12.0")
}
