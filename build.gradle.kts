plugins {
    `java-library`
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.gradleup.shadow") version "9.3.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gorylenko.gradle-git-properties") version "2.5.2"
    id("net.earthmc.conventions.publishing") version "1.1.0"
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://jitpack.io")
}

earthmcPublish {
    public = true
    javadoc = false // javadoc creation is broken, it seems to be pulling in something from paperweight
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paperdevbundle.get())

    // the minimal version we target with userdev may be different from the maximum supported api version
    val paperVersion = libs.versions.paper.get()
    if (paperVersion != libs.versions.minecraft.get()) {
        compileOnly("io.papermc.paper:paper-api:$paperVersion")
    }

    implementation(libs.hikaricp) {
        exclude(group = "org.slf4j")
    }

    implementation(libs.bstats)
    implementation(libs.clickhouse.jdbc)

    compileOnly(platform("com.intellectualsites.bom:bom-newest:1.55"))
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit") {
        exclude(group = "*", module = "FastAsyncWorldEdit-Core")
    }
    compileOnly("com.github.DeadSilenceIV:AdvancedChestsAPI:3.2-BETA")

    testImplementation(platform("org.junit:junit-bom:5.13.1"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-java") {
        select("org.lz4:lz4-java:1.8.0") // use lz4 from clickhouse
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
    }

    shadowJar {
        archiveClassifier.set(libs.versions.minecraft)

        // automatically disable relocations when running via debugger
        val disableRelocation = project.hasProperty("idea.debugger.dispatch.addr") || project.hasProperty("disableRelocation")

        if (!disableRelocation) {
            relocate("org.bstats", "net.coreprotect.libs.bstats")
            relocate("com.zaxxer.hikari", "net.coreprotect.libs.hikaricp")
            relocate("com.clickhouse", "net.coreprotect.libs.clickhouse")
        }

        dependencies {
            exclude(dependency("com.google.code.gson:.*"))
            exclude(dependency("org.intellij:.*"))
            exclude(dependency("org.jetbrains:.*"))
            exclude(dependency("net.java.dev.jna:.*"))
            exclude(dependency("org.jspecify:.*"))
        }
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(25)
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()

        expand(
            "version" to project.version,
            "branch" to "development",
            "api_version" to libs.versions.minecraft.get()
        )
    }

    test {
        useJUnitPlatform()
    }
}

tasks.withType(xyz.jpenilla.runtask.task.AbstractRun::class) {
    javaLauncher = javaToolchains.launcherFor {
        @Suppress("UnstableApiUsage")
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(25)
    }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")
}

gitProperties {
    keys = listOf("git.branch", "git.commit.id", "git.commit.id.abbrev", "git.remote.origin.url", "git.commit.message.short")
}
