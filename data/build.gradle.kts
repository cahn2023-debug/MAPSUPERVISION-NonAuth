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
    implementation(project(":storage-core"))
    implementation(project(":storage-import"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.2")
    // AI
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    // ML Kit
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    
    // MediaPipe LLM - Commented out as it's not yet publicly available in standard Maven repositories
    // implementation("com.google.mediapipe:llm-inference:0.10.7")
}
