import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use(keystoreProperties::load)
}
val releaseStorePassword = System.getenv("NOBS_LAUNCHER_KEYSTORE_PASSWORD")
val releaseKeyPassword = System.getenv("NOBS_LAUNCHER_KEY_PASSWORD") ?: releaseStorePassword
val hasReleaseSigning = keystorePropertiesFile.exists() && !releaseStorePassword.isNullOrBlank()
val gitRevision = providers.exec {
    commandLine("git", "rev-parse", "--short=12", "HEAD")
    workingDir(rootProject.projectDir)
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifBlank { "unknown" }
val hasWorkspaceChanges = providers.exec {
    commandLine("git", "status", "--porcelain", "--untracked-files=normal")
    workingDir(rootProject.projectDir)
    isIgnoreExitValue = true
}.standardOutput.asText.get().isNotBlank()
val buildGitHash = if (gitRevision == "unknown" || !hasWorkspaceChanges) {
    gitRevision
} else {
    "$gitRevision-dirty"
}
val buildInstant = System.getenv("SOURCE_DATE_EPOCH")
    ?.toLongOrNull()
    ?.let(Instant::ofEpochSecond)
    ?: Instant.now()
val buildDateUtc = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
    .withZone(ZoneOffset.UTC)
    .format(buildInstant)

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "dev.basri.android.nobs_launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.basri.android.nobs_launcher"
        minSdk = 23
        targetSdk = 36
        versionCode = 10
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BUILD_GIT_HASH", buildConfigString(buildGitHash))
        buildConfigField("String", "BUILD_DATE_UTC", buildConfigString(buildDateUtc))
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = releaseStorePassword
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    testOptions {
        animationsDisabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.recyclerview)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
}
