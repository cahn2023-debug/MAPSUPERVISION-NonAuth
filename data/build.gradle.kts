plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import java.util.Properties
import java.io.FileInputStream


android {
    namespace = "com.mapsupervision.data"
    compileSdk = 35

    val envFile = rootProject.file(".env")
    val envProperties = Properties()
    if (envFile.exists()) {
        envProperties.load(FileInputStream(envFile))
    }

    defaultConfig { 
        minSdk = 24 
        val geminiKey = envProperties.getProperty("GEMINI_API_KEY") ?: "AIzaSy_YOUR_API_KEY_HERE"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":storage"))

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // AI
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    // ML Kit
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    
    // MediaPipe LLM - Commented out as it's not yet publicly available in standard Maven repositories
    // implementation("com.google.mediapipe:llm-inference:0.10.7")
}
