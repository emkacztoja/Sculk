plugins {
    id("java-library")
}

group = "pl.emkacz"
version = "1.1.0-dev"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:26.1.1-R0.1-SNAPSHOT")

    // Kyori Adventure and MiniMessage for Spigot support (provided at runtime via plugin.yml libraries)
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    compileOnly("net.kyori:adventure-platform-bukkit:4.3.3")

    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:2.11.0")  // Spigot-provided at runtime; not shaded
    testImplementation("org.spigotmc:spigot-api:26.1.1-R0.1-SNAPSHOT")  // for type-safe access in tests
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }
}
