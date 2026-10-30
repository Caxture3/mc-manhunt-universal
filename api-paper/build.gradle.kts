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
publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]); artifactId = "manhunt-universal-api-paper" } }
}

repositories { maven("https://repo.papermc.io/repository/maven-public/") }
