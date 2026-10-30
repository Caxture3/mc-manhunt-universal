import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.language.jvm.tasks.ProcessResources

val minecraftVersion = extra["minecraftVersion"] as String
val javaRelease = extra["javaRelease"] as Int
val loaderVersion = extra["loaderVersion"] as String
val fabricApiVersion = extra["fabricApiVersion"] as String

configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaRelease))
}
tasks.withType<JavaCompile>().configureEach { options.release.set(javaRelease) }

configure<SourceSetContainer> {
    named("main") {
        java.srcDir(rootProject.file("fabric/shared/src/main/java"))
        resources.srcDir(rootProject.file("fabric/shared/src/main/resources"))
    }
    named("client") {
        java.srcDir(rootProject.file("fabric/shared/src/client/java"))
        resources.srcDir(rootProject.file("fabric/shared/src/client/resources"))
    }
    named("test") {
        java.srcDir(rootProject.file("fabric/shared/src/test/java"))
    }
}

val libraries = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies.add("testImplementation", libraries.findLibrary("junit").get())
dependencies.add("testRuntimeOnly", libraries.findLibrary("junit-platform-launcher").get())
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("selector.primaryMouseButton", if (minecraftVersion == "1.21.11") "0" else "1")
}

for (module in listOf(":common", ":protocol")) {
    evaluationDependsOn(module)
    dependencies.add("implementation", dependencies.project(module))
    val moduleOutput = project(module).extensions.getByType<SourceSetContainer>()["main"].output
    tasks.named<Jar>("jar") { from(moduleOutput) }
    tasks.named<Jar>("sourcesJar") {
        from(project(module).extensions.getByType<SourceSetContainer>()["main"].allSource)
    }
}

tasks.named<ProcessResources>("processResources") {
    val values = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to minecraftVersion,
        "java_version" to javaRelease,
        "loader_version" to loaderVersion,
        "fabric_api_version" to fabricApiVersion
    )
    inputs.properties(values)
    filteringCharset = "UTF-8"
    filesMatching("fabric.mod.json") { expand(values) }
}

configure<BasePluginExtension> {
    // Also supplies the archive name to Loom's remap tasks, which are not Jar tasks.
    archivesName.set("manhunt-universal-fabric-mc$minecraftVersion")
}

