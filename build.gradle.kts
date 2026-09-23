plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.0.0"
}

group = "com.esmpfun"
version = "0.3.0"

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
    // Paper API - the 26.3 track (the `-mc263` build). api-version '26.3' in
    // paper-plugin.yml keeps this jar off 26.1/26.2 servers, which is what lets
    // it use API that only exists here: org.bukkit.entity.Cushion and
    // EntityBreakEvent, both new in 26.3 and both needed to protect cushions.
    //
    // 26.1-26.2 lives on `main`, which compiles against 26.1.2 so that nothing
    // newer can slip into that jar.
    compileOnly("io.papermc.paper:paper-api:26.3.build.35-alpha")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Persistence: SQLite (default, zero-setup) + MySQL (opt-in), pooled by HikariCP.
    // SQLite JDBC must NOT be relocated (JNI native lib loading breaks if it is).
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")

    // PluginPulse - update checking + verified install staging.
    implementation("com.github.darkstarworks.PluginPulse:pluginpulse-core:v0.8.0")

    // Anonymous usage metrics (relocated below)
    implementation("dev.faststats.metrics:bukkit:0.28.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.3.build.35-alpha")
}

// MC 26.x runs on JDK 25; Paper 26.3 API classes are compiled to Java 25
// (class file v69), so the build MUST run on a JDK 25 toolchain to read them.
kotlin {
    jvmToolchain(25)
}

tasks {
    shadowJar {
        // The 26.3 build. `main` produces the `-mc26` jar for 26.1-26.2;
        // api-version in paper-plugin.yml is what keeps each jar off the
        // other's servers.
        archiveClassifier.set("mc263")
        // Kotlin stdlib / kotlinx-coroutines NOT relocated - Bukkit must find
        // kotlin.* at runtime. HikariCP relocated to avoid clashing with other
        // plugins' shaded copies.
        relocate("com.zaxxer.hikari", "com.esmpfun.betterend.hikari")
        relocate("io.github.darkstarworks.pluginpulse", "com.esmpfun.betterend.pluginpulse")
        // Relocated so multiple plugins can shade different FastStats versions.
        // Do NOT relocate com.google.gson: FastStats declares it as provided and
        // resolves it from the platform (Paper bundles gson), so it is never
        // shaded here - rewriting those references would break at runtime.
        relocate("dev.faststats", "com.esmpfun.betterend.faststats")
        // Do NOT relocate org.sqlite (JNI native loading) or the MySQL driver
        // (driverClassName references its real package name at runtime).
        mergeServiceFiles()

        // Strip signature files from the (signed) MySQL connector jar - shading a
        // signed jar without this throws "Invalid signature file digest" at load.
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")

        // The FastStats/gson jars are JPMS modules; a merged module-info in a
        // shaded plugin jar is meaningless and trips some class scanners.
        exclude("module-info.class")
        exclude("META-INF/versions/*/module-info.class")

        // Trim SQLite natives to the platforms a MC server actually runs on.
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
