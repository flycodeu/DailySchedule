plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName: String = (project.findProperty("appVersionName") as String?)
    ?.removePrefix("v")
    ?.ifBlank { null }
    ?: "1.0.0"

val appVersionCode: Int = (project.findProperty("appVersionCode") as String?)
    ?.toIntOrNull()
    ?: run {
        val parts = appVersionName.substringBefore("-").split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        major * 10000 + minor * 100 + patch
    }

val githubRepoSlug: String = (project.findProperty("githubRepo") as String?)
    ?.ifBlank { null }
    ?: "flycodeu/DailySchedule"

android {
    namespace = "com.flycode.dailyschedule"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.flycode.dailyschedule"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "GITHUB_REPO", "\"$githubRepoSlug\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
