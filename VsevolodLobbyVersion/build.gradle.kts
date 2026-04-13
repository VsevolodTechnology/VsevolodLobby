plugins {
    java
    eclipse
    idea
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8"
    id("xyz.jpenilla.run-velocity") version "2.3.1"
}

group = "ua.vsevolod.lobby"
version = "1.0"

val velocityApiVersion = "3.5.0-SNAPSHOT"
val viaVersion = "5.7.0"
val targetJavaVersion = 21

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://repo.viaversion.com")
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:$velocityApiVersion")
    annotationProcessor("com.velocitypowered:velocity-api:$velocityApiVersion")
    compileOnly("com.viaversion:viaversion-api:$viaVersion")
}

tasks {
    named<xyz.jpenilla.runvelocity.task.RunVelocity>("runVelocity") {
        velocityVersion(velocityApiVersion)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")

val generateTemplates by tasks.registering(Copy::class) {
    val props = mapOf(
        "version" to project.version.toString()
    )

    inputs.properties(props)
    from(templateSource)
    into(templateDest)
    expand(props)
}

sourceSets {
    named("main") {
        java.srcDir(generateTemplates.map { it.outputs.files })
    }
}

tasks.named("compileJava") {
    dependsOn(generateTemplates)
}

eclipse {
    synchronizationTasks(generateTemplates)
}