import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"), so no org.jetbrains.kotlin.android.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Reads app/google-services.json and initialises Firebase at app start.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.segundoparcialmovil2026_2"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.segundoparcialmovil2026_2"
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google Maps API key, read from local.properties (MAPS_API_KEY=...).
        // local.properties is git-ignored, so the key never lands in source control.
        val mapsApiKey = localProperty("MAPS_API_KEY")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        // Compose / Maps Compose artifacts are built for JVM 17.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// AGP 9 removed android.kotlinOptions; Kotlin compiler config lives in this top-level block.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

/** Reads a value from local.properties (machine-specific, git-ignored). */
fun localProperty(name: String): String {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return ""
    // NOTE: inside a .gradle.kts script `java` is the Gradle java extension,
    // so use the imported type instead of `java.util.Properties`.
    val properties = Properties()
    file.inputStream().use { stream -> properties.load(stream) }
    return properties.getProperty(name, "")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
