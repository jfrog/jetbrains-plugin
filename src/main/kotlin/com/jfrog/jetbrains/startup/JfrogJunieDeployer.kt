// Copyright (c) JFrog Ltd. 2026
// Licensed under the Apache License, Version 2.0
// https://www.apache.org/licenses/LICENSE-2.0

package com.jfrog.jetbrains.startup

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

// Materializes the bundled JFrog assets into the user's Junie home (~/.junie) on
// IDE startup - a compiled plugin can't drop files in place like the file-based
// Cursor/Claude/Codex plugins, so it writes them here (Junie discovers both by
// convention):
//   ~/.junie/skills/       <- vendored jfrog-skills bundle (see VENDOR.md)
//   ~/.junie/mcp/mcp.json  <- a "jfrog" MCP entry (merged, keeping other servers)
class JfrogJunieDeployer : ProjectActivity {
    override suspend fun execute(project: Project) {
        try {
            val junieHome = Path.of(System.getProperty("user.home"), ".junie")
            deploySkills(junieHome.resolve("skills"))
            deployMcpServer(junieHome.resolve("mcp").resolve("mcp.json"))
        } catch (t: Throwable) {
            // Never let a failed deploy break IDE startup; the user can still
            // configure ~/.junie manually (see README "How delivery works").
            LOG.warn("Failed to deploy JFrog assets into ~/.junie", t)
        }
    }

    // Unpacks the bundled skills zip into ~/.junie/skills/. A version marker keeps
    // it a no-op until the plugin version changes. Each JFrog skill dir is wiped
    // first so upstream deletions don't leave stale files; other skills are kept.
    private fun deploySkills(skillsDir: Path) {
        val marker = skillsDir.resolve(MARKER_FILE)
        if (Files.exists(marker) && runCatching { Files.readString(marker).trim() }.getOrNull() == pluginVersion) {
            return
        }

        val stream = javaClass.classLoader.getResourceAsStream(SKILLS_RESOURCE)
        if (stream == null) {
            LOG.warn("Bundled skills archive '$SKILLS_RESOURCE' not found on classpath")
            return
        }

        Files.createDirectories(skillsDir)
        val refreshedSkillDirs = HashSet<String>()
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val top = entry.name.substringBefore('/')
                if (top.isNotEmpty() && refreshedSkillDirs.add(top)) {
                    skillsDir.resolve(top).toFile().deleteRecursively()
                }
                val target = skillsDir.resolve(entry.name).normalize()
                // Zip-slip guard: never write outside the skills directory.
                if (target.startsWith(skillsDir)) {
                    if (entry.isDirectory) {
                        Files.createDirectories(target)
                    } else {
                        Files.createDirectories(target.parent)
                        Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        Files.writeString(marker, pluginVersion)
        LOG.info("Deployed JFrog skills (v$pluginVersion) to $skillsDir")
    }

    // Adds/updates the "jfrog" entry in ~/.junie/mcp/mcp.json, preserving other
    // servers and never overwriting an existing good entry with a placeholder.
    private fun deployMcpServer(mcpFile: Path) {
        val host = resolvePlatformHost()
        val url = if (host != null) "$host/mcp" else "https://$PLATFORM_URL_PLACEHOLDER/mcp"

        val root: JsonObject = if (Files.exists(mcpFile)) {
            runCatching { JsonParser.parseString(Files.readString(mcpFile)).asJsonObject }.getOrElse { JsonObject() }
        } else {
            JsonObject()
        }

        val servers = root.getAsJsonObject("mcpServers") ?: JsonObject().also { root.add("mcpServers", it) }
        val existing = servers.getAsJsonObject("jfrog")

        // Don't stomp a working, user-resolved URL with a placeholder.
        if (host == null && existing != null) return
        if (existing != null && existing.get("url")?.asString == url) return

        servers.add("jfrog", JsonObject().apply { addProperty("url", url) })
        Files.createDirectories(mcpFile.parent)
        Files.writeString(mcpFile, GsonBuilder().setPrettyPrinting().create().toJson(root))
        LOG.info("Configured JFrog MCP server in $mcpFile (url=$url)")
    }

    // Prefer JFROG_PLATFORM_URL, else the jf CLI config (a GUI-launched IDE
    // doesn't inherit the shell env). We read the config file directly since
    // `jf` may not be on the IDE's PATH.
    private fun resolvePlatformHost(): String? {
        val env = System.getenv("JFROG_PLATFORM_URL")
        if (!env.isNullOrBlank()) return normalizeHost(env)
        return hostFromJfrogCliConfig()
    }

    // Tolerate a trailing slash or an already-present scheme.
    private fun normalizeHost(raw: String): String? {
        val h = raw.trim().trimEnd('/')
        if (h.isEmpty()) return null
        return if (h.startsWith("http://") || h.startsWith("https://")) h else "https://$h"
    }

    // Read the host from the highest-versioned ~/.jfrog/jfrog-cli.conf.v* (JSON).
    private fun hostFromJfrogCliConfig(): String? {
        val jfrogDir = Path.of(System.getProperty("user.home"), ".jfrog")
        if (!Files.isDirectory(jfrogDir)) return null
        val conf = Files.list(jfrogDir).use { paths ->
            paths.filter { it.fileName.toString().startsWith("jfrog-cli.conf.v") }
                .max(compareBy { confVersion(it.fileName.toString()) })
                .orElse(null)
        } ?: return null
        val root = runCatching { JsonParser.parseString(Files.readString(conf)).asJsonObject }.getOrNull() ?: return null
        val servers = root.getAsJsonArray("servers") ?: return null
        if (servers.size() == 0) return null
        val serverObjs = (0 until servers.size()).map { servers[it].asJsonObject }
        // One server is unambiguous; otherwise prefer the default, then the first.
        val server = if (serverObjs.size == 1) {
            serverObjs[0]
        } else {
            serverObjs.firstOrNull { it.get("isDefault")?.asBoolean == true } ?: serverObjs[0]
        }
        val url = server.get("url")?.asString ?: return null
        return normalizeHost(url)
    }

    private fun confVersion(name: String): Int =
        name.removePrefix("jfrog-cli.conf.v").toIntOrNull() ?: -1

    private val pluginVersion: String
        get() = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "dev"

    private companion object {
        val LOG = logger<JfrogJunieDeployer>()
        const val PLUGIN_ID = "com.jfrog.jetbrains"
        const val SKILLS_RESOURCE = "junie/junie-skills.zip"
        const val MARKER_FILE = ".jfrog-plugin-version"
        const val PLATFORM_URL_PLACEHOLDER = "<JFROG_PLATFORM_URL>"
    }
}
