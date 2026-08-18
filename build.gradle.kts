import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")

        testFramework(TestFrameworkType.Platform)
    }
}

// Zip the vendored skill tree into the jar so JfrogJunieDeployer can unpack it
// into ~/.junie/skills/ at startup (a jar can't enumerate a bundled dir). The
// JFrog MCP entry is written in code, so .junie/mcp/mcp.json is a repo template.
val bundleJunieSkills by tasks.registering(Zip::class) {
    from(layout.projectDirectory.dir(".junie/skills"))
    archiveFileName.set("junie-skills.zip")
    destinationDirectory.set(layout.buildDirectory.dir("tmp/junie-assets"))
}

tasks.processResources {
    dependsOn(bundleJunieSkills)
    from(bundleJunieSkills) { into("junie") }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("version")

        ideaVersion {
            // 2025.2 (build 252) is the floor: Junie ships in 2025.2+. No upper
            // bound, so the plugin stays compatible with future IDE releases.
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            current()
        }
    }

    // Marketplace requires signed plugins. signPlugin runs before publishPlugin
    // when these env vars are set, and is skipped for a plain local buildPlugin.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // publishPlugin uploads to Marketplace with a personal token. The first
    // version must be uploaded once via the web UI before token publishing works.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf("default")
    }
}
