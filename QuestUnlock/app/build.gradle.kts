plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "qu.astro.vrshellpatcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "qu.astro.vrshellpatcher"
        minSdk = 31              // keep 31 for Quest/Android 12 base (bump down if you want)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            // handy if you want debug+release installed side-by-side
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
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
        // desugaring not required; using plain android.app.* APIs
    }

    buildFeatures {
        buildConfig = true
    }

    // no viewBinding/databinding; plain Activity UI
}

dependencies {
    // Xposed API is provided at runtime by LSPosed
    compileOnly("de.robv.android.xposed:api:82")
    // No other deps needed for your plain Activity
}
