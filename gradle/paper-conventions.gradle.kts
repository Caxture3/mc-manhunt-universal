import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources

val minecraftVersion = extra["minecraftVersion"] as String
val javaRelease = extra["javaRelease"] as Int

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaRelease))
}
tasks.withType<JavaCompile>().configureEach { options.release.set(javaRelease) }

configure<SourceSetContainer> {
    named("main") {
        java.srcDir(rootProject.file("paper/shared/src/main/java"))
        resources.srcDir(rootProject.file("paper/shared/src/main/resources"))
    }
}

// Compile against and embed the pure Java modules; no separate runtime installation needed.
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
    val values = mapOf("version" to project.version.toString(), "minecraft_version" to minecraftVersion)
    inputs.properties(values)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(values) }
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("manhunt-universal-paper")
    archiveAppendix.set("mc$minecraftVersion")
}

