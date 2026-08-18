// Copyright (c) JFrog Ltd. 2026
// Licensed under the Apache License, Version 2.0
// https://www.apache.org/licenses/LICENSE-2.0

package com.jfrog.jetbrains.startup

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

// On IDE startup, writes the bundled JFrog assets into the user's Junie home
// (~/.junie) so Junie loads them:
//   ~/.junie/skills/       <- vendored jfrog-skills bundle (see VENDOR.md)
//   ~/.junie/mcp/mcp.json  <- a "jfrog" MCP entry (merged, keeping other servers)
//
// Several projects can open at once and run this activity concurrently, so the
// work is serialized on DEPLOY_LOCK and mcp.json is written atomically.
class JfrogJunieDeployer : ProjectActivity {
    override suspend fun execute(project: Project) {
        try {
            synchronized(DEPLOY_LOCK) {
                val junieHome = Path.of(System.getProperty("user.home"), ".junie")
                deploySkills(junieHome.resolve("skills"))
                deployMcpServer(junieHome.resolve("mcp").resolve("mcp.json"))
            }
        } catch (t: Throwable) {
            // Never let a failed deploy break IDE startup, but surface it so the
            // user knows to configure ~/.junie manually (see README).
            LOG.warn("Failed to deploy JFrog assets into ~/.junie", t)
            notifyFailure(project, t)
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
                val target = skillsDir.resolve(entry.name).normalize()
                // Zip-slip guard FIRST: skip anything that resolves outside
                // skillsDir, so neither the wipe nor the write can escape it.
                if (target.startsWith(skillsDir)) {
                    // Top-level skill dir, taken from the guarded path (safe).
                    val top = skillsDir.relativize(target).firstOrNull()?.toString()
                    if (!top.isNullOrEmpty() && refreshedSkillDirs.add(top)) {
                        skillsDir.resolve(top).toFile().deleteRecursively()
                    }
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

        val current = if (Files.exists(mcpFile)) Files.readString(mcpFile) else null
        val merged = JfrogDeployLogic.mergeMcpJson(current, url, urlIsPlaceholder = host == null)
        if (merged == null) return // nothing to change (or unparseable file we won't clobber)

        writeAtomically(mcpFile, merged)
        LOG.info("Configured JFrog MCP server in $mcpFile (url=$url)")
    }

    // Prefer JFROG_PLATFORM_URL, else the jf CLI config (a GUI-launched IDE
    // doesn't inherit the shell env). We read the config file directly since
    // `jf` may not be on the IDE's PATH.
    private fun resolvePlatformHost(): String? {
        val env = System.getenv("JFROG_PLATFORM_URL")
        if (!env.isNullOrBlank()) return JfrogDeployLogic.normalizeHost(env)

        val jfrogDir = Path.of(System.getProperty("user.home"), ".jfrog")
        if (!Files.isDirectory(jfrogDir)) return null
        val conf = Files.list(jfrogDir).use { paths ->
            paths.filter { it.fileName.toString().startsWith("jfrog-cli.conf.v") }
                .max(compareBy { confVersion(it.fileName.toString()) })
                .orElse(null)
        } ?: return null
        return JfrogDeployLogic.selectHostFromCliConfig(Files.readString(conf))
    }

    // Write via a temp file in the same directory, then atomically rename over the
    // target so a concurrent reader never sees a half-written file.
    private fun writeAtomically(target: Path, content: String) {
        Files.createDirectories(target.parent)
        val tmp = Files.createTempFile(target.parent, ".${target.fileName}", ".tmp")
        try {
            Files.writeString(tmp, content)
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                // ATOMIC_MOVE isn't supported on every filesystem; fall back.
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun notifyFailure(project: Project, t: Throwable) {
        runCatching {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("JFrog")
                .createNotification(
                    "JFrog setup incomplete",
                    "Couldn't set up JFrog skills / MCP in ~/.junie: ${t.message ?: t.javaClass.simpleName}. " +
                        "See the plugin README (\"How delivery works\") to configure it manually.",
                    NotificationType.WARNING,
                )
                .notify(project)
        }
    }

    private fun confVersion(name: String): Int =
        name.removePrefix("jfrog-cli.conf.v").toIntOrNull() ?: -1

    private val pluginVersion: String
        get() = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "dev"

    private companion object {
        val LOG = logger<JfrogJunieDeployer>()
        val DEPLOY_LOCK = Any()
        const val PLUGIN_ID = "com.jfrog.jetbrains"
        const val SKILLS_RESOURCE = "junie/junie-skills.zip"
        const val MARKER_FILE = ".jfrog-plugin-version"
        const val PLATFORM_URL_PLACEHOLDER = "<JFROG_PLATFORM_URL>"
    }
}

internal object JfrogDeployLogic {

    // Normalizes a host into a scheme-qualified base URL, tolerating a trailing
    // slash or an already-present scheme. Returns null for blank input.
    fun normalizeHost(raw: String?): String? {
        val h = raw?.trim()?.trimEnd('/').orEmpty()
        if (h.isEmpty()) return null
        return if (h.startsWith("http://") || h.startsWith("https://")) h else "https://$h"
    }

    // Picks the JFrog host from a jfrog-cli config JSON. One server is
    // unambiguous; with several, only a server flagged default is used. With
    // several and none flagged we do NOT guess: startup is non-interactive so we
    // can't ask, and callers fall back to the manual placeholder instead.
    fun selectHostFromCliConfig(configJson: String): String? {
        val root = runCatching { JsonParser.parseString(configJson).asJsonObject }.getOrNull() ?: return null
        val servers = root.getAsJsonArray("servers") ?: return null
        val serverObjs = servers.mapNotNull { it as? JsonObject }
        val server = when {
            serverObjs.isEmpty() -> return null
            serverObjs.size == 1 -> serverObjs[0]
            else -> serverObjs.firstOrNull { it.boolOrFalse("isDefault") } ?: return null
        }
        return normalizeHost(server.stringOrNull("url"))
    }

    // Merges a "jfrog" server entry into an existing mcp.json, preserving every
    // other server. Returns the JSON text to write, or null when nothing should
    // change: an unparseable current file, an existing entry when we only have
    // a placeholder, or an already-correct URL.
    fun mergeMcpJson(currentJson: String?, jfrogUrl: String, urlIsPlaceholder: Boolean): String? {
        val root: JsonObject = if (currentJson.isNullOrBlank()) {
            JsonObject()
        } else {
            runCatching { JsonParser.parseString(currentJson).asJsonObject }.getOrNull() ?: return null
        }

        val servers = root.getAsJsonObject("mcpServers") ?: JsonObject().also { root.add("mcpServers", it) }
        val existing = servers.getAsJsonObject("jfrog")

        if (urlIsPlaceholder && existing != null) return null
        if (existing != null && existing.stringOrNull("url") == jfrogUrl) return null

        servers.add("jfrog", JsonObject().apply { addProperty("url", jfrogUrl) })
        return GsonBuilder().setPrettyPrinting().create().toJson(root)
    }

    // Returns the string value only for an actual JSON string; null for a missing
    // key, JSON null, or non-string - so `?.asString` can never throw on JsonNull.
    private fun JsonObject.stringOrNull(key: String): String? {
        val el = get(key) ?: return null
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isString) el.asString else null
    }

    private fun JsonObject.boolOrFalse(key: String): Boolean {
        val el = get(key) ?: return false
        return el.isJsonPrimitive && el.asJsonPrimitive.isBoolean && el.asBoolean
    }
}
