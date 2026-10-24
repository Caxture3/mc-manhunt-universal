plugins { alias(libs.plugins.loom.remap) }

val minecraftVersion = libs.versions.minecraft12111.get()
extra["minecraftVersion"] = minecraftVersion
extra["javaRelease"] = 21
extra["loaderVersion"] = libs.versions.fabric.loader.get()
extra["fabricApiVersion"] = libs.versions.fabric.api12111.get()

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
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api12111)
}

apply(from = rootProject.file("gradle/fabric-conventions.gradle.kts"))

