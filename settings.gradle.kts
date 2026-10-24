pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "mc-manhunt-universal"

include("common", "protocol")

val supportedVersions = listOf("1.21.11", "26.3")
val selectedVersion = providers.gradleProperty("mcVersion").orNull
require(selectedVersion == null || selectedVersion in supportedVersions) {
    "Unsupported mcVersion '$selectedVersion'. Supported versions: ${supportedVersions.joinToString()}"
}

for (minecraftVersion in selectedVersion?.let(::listOf) ?: supportedVersions) {
    for (platform in listOf("paper", "fabric")) {
        val moduleName = "$platform-$minecraftVersion"
        include(moduleName)
        project(":$moduleName").projectDir = file("$platform/$minecraftVersion")
    }
}

