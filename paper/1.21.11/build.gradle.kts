plugins { java }

val minecraftVersion = libs.versions.minecraft12111.get()
extra["minecraftVersion"] = minecraftVersion
extra["javaRelease"] = 21

dependencies { compileOnly(libs.paper12111) }
apply(from = rootProject.file("gradle/paper-conventions.gradle.kts"))

