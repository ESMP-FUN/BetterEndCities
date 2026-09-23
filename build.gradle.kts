plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.0.0"
}

group = "com.esmpfun"
version = "0.4.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://jitpack.io")
    maven("https://repo.faststats.dev/releases") {
        name = "faststatsReleases"
    }
}

dependencies {
    // The -mc26 jar, built against the oldest supported API so nothing newer slips in.
    // The mc263 branch builds the -mc263 jar against 26.3 for the cushion API.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.72-stable")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // SQLite must not be relocated: its native library loading breaks.
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")

    implementation("com.github.darkstarworks.PluginPulse:pluginpulse-core:v0.8.0")

    implementation("dev.faststats.metrics:bukkit:0.28.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.72-stable")
}

// The Paper 26 API is compiled for Java 25.
kotlin {
    jvmToolchain(25)
}

tasks {
    shadowJar {
        archiveClassifier.set("mc26")
        // Kotlin is not relocated; everything that can clash with other plugins is.
        relocate("com.zaxxer.hikari", "com.esmpfun.betterend.hikari")
        relocate("io.github.darkstarworks.pluginpulse", "com.esmpfun.betterend.pluginpulse")
        // Not gson: FastStats uses the copy Paper ships.
        relocate("dev.faststats", "com.esmpfun.betterend.faststats")
        // Not org.sqlite (native loading) or the MySQL driver (loaded by class name).
        mergeServiceFiles()

        // The MySQL jar is signed; its signatures would fail once shaded.
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")

        exclude("module-info.class")
        exclude("META-INF/versions/*/module-info.class")

        exclude("org/sqlite/native/FreeBSD/**")
        exclude("org/sqlite/native/Linux-Android/**")
        exclude("org/sqlite/native/Linux-Musl/**")
        exclude("org/sqlite/native/Mac/**")
    }
    jar {
        enabled = false
    }
    build {
        dependsOn(shadowJar)
    }
    test {
        useJUnitPlatform()
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
