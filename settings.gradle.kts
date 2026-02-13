pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "gno-ide"

include(":gno-goland-plugin")
project(":gno-goland-plugin").projectDir = file("gno-goland-plugin")
