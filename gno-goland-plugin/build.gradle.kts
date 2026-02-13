import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "io.gnolang.ide"
version = "0.3.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    intellijPlatform {
        goland("2024.3")
        bundledPlugin("org.jetbrains.plugins.go")
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
        }

        changeNotes =
            """
            Improved Gno support for GoLand:
            <ul>
              <li>Treat <code>.gno</code> files as Go files for native editing support.</li>
              <li>Treat <code>gno.mod</code> as <code>go.mod</code> for native module-file editing support.</li>
              <li>Suppress common false-positive warnings and inspections in <code>.gno</code> files.</li>
              <li>Add import support for <code>std</code>, plus <code>gno.land/p/nt/*</code> and <code>gno.land/p/demo/*</code> variants of <code>ufmt</code>, <code>avl</code>, and <code>testutils</code>.</li>
              <li>Treat <code>realm</code>, <code>address</code>, and <code>cross</code> as built-in language symbols (no import).</li>
              <li>Add a <code>Gno</code> Run Configuration for running <code>gno</code> CLI commands.</li>
            </ul>
            """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        options.release.set(21)
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
}
