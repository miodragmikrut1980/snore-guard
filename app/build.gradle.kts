import java.io.File
import java.net.HttpURLConnection
import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing credentials exist ONLY as environment variables in a trusted release job.
// Never commit or print the private keystore or password.
val privateReleaseStore = System.getenv("SNOREGUARD_SIGNING_STORE_FILE")
val privateReleaseStorePassword = System.getenv("SNOREGUARD_SIGNING_STORE_PASSWORD")
val privateReleaseAlias = System.getenv("SNOREGUARD_SIGNING_KEY_ALIAS")
val privateReleaseKeyPassword = System.getenv("SNOREGUARD_SIGNING_KEY_PASSWORD")
val privateReleaseConfigured = listOf(
    privateReleaseStore, privateReleaseStorePassword,
    privateReleaseAlias, privateReleaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "rs.snoreguard.mvp"
    compileSdk = 35

    defaultConfig {
        applicationId = "rs.snoreguard.mvp"
        minSdk = 26
        targetSdk = 35
        versionCode = 81
        versionName = "0.8.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (privateReleaseConfigured) {
            create("privateRelease") {
                storeFile = file(privateReleaseStore!!)
                storePassword = privateReleaseStorePassword!!
                keyAlias = privateReleaseAlias!!
                keyPassword = privateReleaseKeyPassword!!
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (privateReleaseConfigured) {
                signingConfig = signingConfigs.getByName("privateRelease")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "tflite" }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/yamnetAssets"))
}

/**
 * Model is downloaded on the developer's computer during the first build.
 * Its audio inference is COMPLETELY OFFLINE on the phone. No INTERNET app permission.
 * Not bundled in this ZIP because the container cannot retrieve binary assets.
 */
val modelFile = layout.buildDirectory.file("generated/yamnetAssets/yamnet.tflite")
val downloadYamnet by tasks.registering {
    group = "setup"
    description = "Fetches the YAMNet TFLite model once from the official MediaPipe model store"
    outputs.file(modelFile)
    doLast {
        val dest = modelFile.get().asFile
        if (dest.isFile && dest.length() > 3_000_000L) return@doLast
        dest.parentFile.mkdirs()
        val temporary = File(dest.parentFile, "yamnet.tflite.part")
        val url = "https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/latest/yamnet.tflite"
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 25_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "SnoreGuard-MVP/0.3")
            try {
                check(conn.responseCode == 200) { "HTTP ${conn.responseCode} while fetching YAMNet" }
                conn.inputStream.use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
            } finally { conn.disconnect() }
            check(temporary.length() > 3_000_000L) { "YAMNet model download incomplete: ${temporary.length()} B" }
            check(temporary.renameTo(dest)) { "Cannot move model into assets" }
        } catch (ex: Exception) {
            temporary.delete()
            throw GradleException("YAMNet model download failed. Check internet access, then Sync/Build again. URL: $url", ex)
        }
    }
}

tasks.named("preBuild").configure { dependsOn(downloadYamnet) }

dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
}
