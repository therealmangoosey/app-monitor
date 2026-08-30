plugins {
    id("com.android.application")
}

android {
    namespace = "com.therealmangoosey.appmonitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.therealmangoosey.appmonitor"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.2"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
