import java.text.SimpleDateFormat
import java.util.Date

// ============================================================
// Datei:    build.gradle.kts
// Autor:    Carlo
// Erstellt: 2026-03-24
// Stand:    1.3
// Projekt:  CarMa
//
// HISTORY:
// 2026-04-22  v1.3  Header: Stand statt Version; Versionsformat x.y.z → x.z
//                   androidVersion wird in version.json geschrieben
// 2026-04-11  v1.2  Automatische Versionierung über Git:
//                   versionCode = Anzahl Git-Commits (git rev-list --count HEAD)
//                   versionName = Git-Tag (z.B. v1.3) oder Datum (YYYY.MM.DD)
// ============================================================

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun gitVersionCode(): Int {
    return try {
        ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootDir)
            .start()
            .inputStream.bufferedReader().readText().trim().toInt()
    } catch (e: Exception) { 1 }
}

fun gitVersionName(): String {
    return try {
        val proc = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .directory(rootDir)
            .start()
        proc.waitFor()
        val tag = proc.inputStream.bufferedReader().readText().trim()
        if (tag.isNotEmpty()) tag.removePrefix("v")
        else dateFallback()
    } catch (e: Exception) { dateFallback() }
}

fun dateFallback(): String {
    return SimpleDateFormat("yyyy.MM.dd").format(Date())
}

android {
    namespace = "com.carma"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.carma"
        minSdk = 24
        targetSdk = 36
        versionCode = gitVersionCode()
        versionName = gitVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true  // Für BuildConfig.VERSION_CODE im Auto-Update-Check
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}