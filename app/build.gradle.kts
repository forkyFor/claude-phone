plugins {
    id("com.android.application")
}

// Release signing comes only from the environment (GitHub Secrets in CI).
// Nothing is read from the repository. See docs/en/SIGNING.md.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_FILE")
val hasSigning = keystorePath != null && file(keystorePath).exists()

// GeckoView version used by the "gecko" flavor. 144+ requires Android 8.0 (API 26).
val geckoVersion = "160.0.20261104181552"

android {
    namespace = "io.github.forkyfor.oldphone"
    // GeckoView 160 needs API 37 to compile against. This does not change which phones can run the app.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "io.github.forkyfor.oldphone"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "REPO_URL", "\"https://github.com/forkyFor/claude-phone\"")
    }

    androidResources {
        localeFilters += listOf("en", "it")
    }

    flavorDimensions += "engine"
    productFlavors {
        // System WebView only: tiny APK, Android 5.0+.
        create("lite") {
            dimension = "engine"
            buildConfigField("boolean", "HAS_GECKO", "false")
        }
        // System WebView + bundled GeckoView, selectable at runtime: large APK, Android 8.0+.
        create("gecko") {
            dimension = "engine"
            minSdk = 26
            versionNameSuffix = "-gecko"
            buildConfigField("boolean", "HAS_GECKO", "true")
            ndk {
                // -Pabi=arm64-v8a builds a single-ABI APK (what CI publishes).
                val abi = (project.findProperty("abi") as String?)
                abiFilters += abi?.split(",") ?: listOf("armeabi-v7a", "arm64-v8a")
            }
        }
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += listOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin")
    }

    dependenciesInfo {
        // No encrypted dependency blob in the APK (F-Droid requirement, and nobody needs it).
        includeInApk = false
        includeInBundle = false
    }

    lint {
        // Checks that do not apply here: two flavors with different minSdk, no Play Store
        // bundle, no AndroidX, and an https filter that deliberately is not a verified App Link.
        disable += listOf(
            "SetJavaScriptEnabled", "OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion",
            "ObsoleteSdkInt", "AppLinkWarning", "AppBundleLocaleChanges", "ChromeOsAbiSupport",
            "WebViewApiAvailability", "PluralsCandidate", "ViewConstructor", "ApplySharedPref",
        )
        warningsAsErrors = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Deliberately no AndroidX / Material / Compose: framework APIs only.
    "geckoImplementation"("org.mozilla.geckoview:geckoview:$geckoVersion")
    testImplementation("junit:junit:4.13.2")
}
