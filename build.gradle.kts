plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.vanniktech.dependency.graph.generator") version "0.8.0" apply false
}

subprojects {
    tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
        exclude("**/byRounds/**")
    }
}

tasks.register("enforceModuleBoundaries") {
    group = "verification"
    description = "Fails if forbidden feature-to-feature imports are detected."
    doLast {
        val violations = mutableListOf<String>()
        val gisSources = fileTree("gis/src/main/java") { include("**/*.kt") }
        gisSources.forEach { source ->
            val text = source.readText()
            if (text.contains("com.mapsupervision.reporting")) {
                violations += "Forbidden import in ${source.path}: :gis must not depend on :reporting"
            }
        }
        if (violations.isNotEmpty()) {
            error(violations.joinToString(separator = "\n"))
        }
    }
}
