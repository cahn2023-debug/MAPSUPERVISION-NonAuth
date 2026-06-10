plugins {
    id("com.android.application") version "8.10.0" apply false
    id("com.android.library") version "8.10.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    id("com.vanniktech.dependency.graph.generator") version "0.8.0" apply false
}

import org.gradle.api.tasks.compile.JavaCompile

val projectDependencyPattern = Regex("""project\("(:[^"]+)"\)""")
val allowedProjectDependencies = mapOf(
    ":app" to setOf(":core", ":domain", ":data", ":project", ":gis", ":gis-maplibre", ":photo", ":timeline", ":reporting", ":storage", ":storage-core", ":storage-crypto", ":storage-import"),
    ":core" to emptySet(),
    ":data" to setOf(":core", ":domain", ":storage", ":storage-core", ":storage-crypto", ":storage-import"),
    ":domain" to setOf(":core"),
    ":gis" to setOf(":core", ":domain", ":storage", ":storage-core", ":storage-crypto"),
    ":gis-maplibre" to setOf(":gis", ":domain"),
    ":photo" to setOf(":core", ":domain", ":data", ":storage", ":storage-core", ":storage-crypto"),
    ":project" to setOf(":core", ":domain", ":storage", ":storage-core", ":storage-crypto", ":storage-import"),
    ":reporting" to setOf(":core", ":domain", ":storage", ":storage-core", ":storage-crypto"),
    ":storage" to setOf(":storage-core", ":storage-crypto"),
    ":storage-core" to setOf(":core", ":domain"),
    ":storage-crypto" to emptySet(),
    ":storage-import" to setOf(":core", ":domain", ":storage", ":storage-core", ":storage-crypto"),
    ":timeline" to setOf(":core", ":domain", ":storage", ":storage-core", ":storage-crypto")
)

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        exclude("**/byRounds/**")
    }
}

tasks.register<EnforceModuleBoundariesTask>("enforceModuleBoundaries") {
    group = "verification"
    description = "Fails if project modules depend on disallowed project modules."
    buildFiles.from(rootProject.subprojects.map { it.buildFile })
    allowedDependencies.putAll(allowedProjectDependencies.mapValues { (_, value) -> value.joinToString(",") })
}

tasks.register("check") {
    group = "verification"
    description = "Aggregates module verification tasks and module boundary checks."
}

gradle.projectsEvaluated {
    val moduleCheckTasks = subprojects.mapNotNull { it.tasks.findByName("check") }
    tasks.named("check").configure {
        dependsOn(moduleCheckTasks)
        dependsOn("enforceModuleBoundaries")
    }
}
