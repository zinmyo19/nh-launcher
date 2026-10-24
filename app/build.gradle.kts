import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingProps = Properties().apply {
    // Optional: point NH_SIGNING_PROPS at a file with storeFile/storePassword/keyAlias/keyPassword,
    // or put "signing.properties" in the project root. Without it the build is simply unsigned/debug-signed.
    val f = System.getenv("NH_SIGNING_PROPS")?.let { file(it) } ?: rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
android {
    namespace = "com.zynelabs.nhlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zynelabs.nhlauncher"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.1"
    }
    signingConfigs {
        create("nh") {
            if (signingProps.containsKey("storeFile")) {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug { if (signingProps.containsKey("storeFile")) signingConfig = signingConfigs.getByName("nh") }
        release {
            if (signingProps.containsKey("storeFile")) signingConfig = signingConfigs.getByName("nh")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    sourceSets["main"].java.srcDir("src/main/kotlin")
    lint { abortOnError = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
}
