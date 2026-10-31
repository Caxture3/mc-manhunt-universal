plugins { `java-library`; `maven-publish` }

version = libs.versions.extension.api.get()
base { archivesName.set("manhunt-universal-api-paper") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)); withJavadocJar() }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

dependencies {
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
    api(project(":api"))
    compileOnly(libs.paper12111)
    testImplementation(libs.paper12111)
}
tasks.test { useJUnitPlatform() }
val examples = sourceSets.create("examples") {
    java.srcDir(rootProject.file("examples/api-1.1/src/main/java"))
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}
tasks.named("check") { dependsOn(tasks.named(examples.compileJavaTaskName)) }
publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]); artifactId = "manhunt-universal-api-paper" } }
}

repositories { maven("https://repo.papermc.io/repository/maven-public/") }
