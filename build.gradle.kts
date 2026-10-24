import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile

plugins { base }

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral {
            content { excludeGroup("io.papermc.paper") }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> { withSourcesJar() }
    }
}

// Root lifecycle tasks cover every module selected in settings.gradle.kts.
tasks.named("assemble") { dependsOn(subprojects.map { "${it.path}:assemble" }) }
tasks.named("check") { dependsOn(subprojects.map { "${it.path}:check" }) }
tasks.named("clean") { dependsOn(subprojects.map { "${it.path}:clean" }) }

