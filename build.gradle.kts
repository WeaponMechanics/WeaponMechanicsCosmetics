/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml

group = "com.cjcrafter"
version = "4.3.0"

plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
}

paperPluginYaml {
    main = "com.cjcrafter.weaponmechanicscosmetics.WeaponMechanicsCosmetics"
    name = "WeaponMechanicsCosmetics"
    apiVersion = "1.21"
    foliaSupported = true

    authors = listOf("CJCrafter", "DeeCaaD")
    dependencies {
        server("packetevents", required = true, load = PaperPluginYaml.Load.BEFORE)
        server("MechanicsCore", required = true, load = PaperPluginYaml.Load.BEFORE)
        server("WeaponMechanics", required = true, load = PaperPluginYaml.Load.BEFORE)
        server("VivecraftSpigot", required = false)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots/") // MechanicsCore Snapshots
    maven(url = "https://repo.papermc.io/repository/maven-public/") // Paper
    maven(url = "https://repo.maven.apache.org/maven2/")
    maven(url = "https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("org.jetbrains:annotations:24.0.1")

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")

    compileOnly("com.cjcrafter:mechanicscore:4.3.0-SNAPSHOT")
    compileOnly("com.cjcrafter:weaponmechanics:4.2.0-SNAPSHOT")
    compileOnly("com.cjcrafter:vivecraft:3.0.0")
    compileOnly("com.github.retrooper:packetevents-spigot:2.8.0")
    compileOnly("com.github.cryptomorin:XSeries:13.7.0")
    compileOnly("com.cjcrafter:foliascheduler:0.7.5")
    compileOnly("dev.jorel:commandapi-paper-core:11.1.0")
    compileOnly("org.bstats:bstats-bukkit:3.0.1")

    implementation("org.mariuszgromada.math:MathParser.org-mXparser:5.2.1")
}

tasks.shadowJar {
    archiveFileName.set("WeaponMechanicsCosmetics-${project.version}.jar")

    relocate("org.mariuszgromada.math", "com.cjcrafter.weaponmechanicscosmetics.lib.math")
    relocate("org.bstats", "me.deecaad.core.lib.bstats")
    relocate("com.cryptomorin.xseries", "me.deecaad.core.lib.xseries")
    relocate("com.cjcrafter.foliascheduler", "me.deecaad.core.lib.scheduler")
    relocate("dev.jorel.commandapi", "me.deecaad.core.lib.commandapi")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
        options.release.set(21)
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
}

tasks.test {
    useJUnitPlatform()
}