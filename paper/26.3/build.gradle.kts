plugins { java }

val minecraftVersion = libs.versions.minecraft263.get()
extra["minecraftVersion"] = minecraftVersion
extra["javaRelease"] = 25

dependencies { compileOnly(libs.paper263) }
apply(from = rootProject.file("gradle/paper-conventions.gradle.kts"))

