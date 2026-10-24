plugins { alias(libs.plugins.loom) }

val minecraftVersion = libs.versions.minecraft263.get()
extra["minecraftVersion"] = minecraftVersion
extra["javaRelease"] = 25
extra["loaderVersion"] = libs.versions.fabric.loader.get()
extra["fabricApiVersion"] = libs.versions.fabric.api263.get()

loom {
    splitEnvironmentSourceSets()
    mods {
        create("randomanhunt") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api263)
}

apply(from = rootProject.file("gradle/fabric-conventions.gradle.kts"))

