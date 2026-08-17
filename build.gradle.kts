import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    kotlin("plugin.serialization") version "2.1.20"
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")

        // Plugin that ships the IDE's built-in MCP server (id com.intellij.mcpServer,
        // package com.intellij.mcpserver) - our JfrogToolset implements its
        // McpToolset marker interface. Declared as a *bundled* plugin dependency
        // (not plugin(id, version), which resolves the older, deprecated
        // standalone Marketplace plugin with an incompatible API) because MCP
        // server support is now built into the IDE distribution itself (2025.2+).
        // Confirmed by decompiling the actual bundled jar - see CONTRIBUTING.md
        // "Known open risk".
        bundledPlugin("com.intellij.mcpServer")

        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("version")

        ideaVersion {
            // 2025.2 (build 252) is the floor: the plugin needs the IDE's built-in
            // com.intellij.mcpServer. Upper bound covers current org IDEs (2026.2 =
            // build 262); the McpToolset/@McpTool API is stable across 252..262.
            sinceBuild = "252"
            untilBuild = "262.*"
        }
    }

    // Marketplace requires signed plugins; signPlugin runs before publishPlugin when
    // these env vars are set, and is skipped for a plain local buildPlugin.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // publishPlugin uploads to Marketplace with a personal token; the first version
    // must be uploaded manually via the web UI before token publishing works.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf("default")
    }

    pluginVerification {
        ides {
            current()
        }
    }
}
