plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
}

group = "ua.vsevolod"
version = "1.0.0"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.opencollab.dev/maven-releases/") }


    maven("https://repo.hypera.dev/snapshots/")
    maven("https://repo.lucko.me/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}


tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    manifest {
        attributes(
            "Main-Class" to "ua.vsevolod.lobby.bootstrap.Main"
        )
    }
}

dependencies {
    implementation("net.minestom:minestom:2026.05.11-1.21.11")
    implementation("com.google.zxing:core:3.5.3")
    implementation("dev.lu15:spark-minestom:1.10-SNAPSHOT")
    implementation("org.mongodb:mongodb-driver-sync:5.2.1")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.yaml:snakeyaml:2.3")
    implementation("de.exlll:configlib-yaml:4.8.1")
    implementation("net.kyori:adventure-text-minimessage:4.26.1")
    implementation("dev.lu15:luckperms-minestom:5.5-SNAPSHOT")
    implementation("com.h2database:h2:2.2.224")
}

tasks.test {
    useJUnitPlatform()
}
