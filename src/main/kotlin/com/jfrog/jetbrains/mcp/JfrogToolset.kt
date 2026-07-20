// Copyright (c) JFrog Ltd. 2026
// Licensed under the Apache License, Version 2.0
// https://www.apache.org/licenses/LICENSE-2.0

package com.jfrog.jetbrains.mcp

import com.intellij.mcpserver.McpToolset
import kotlinx.serialization.Serializable

@Serializable
data class ArtifactorySearchResult(val query: String, val repository: String?, val matches: List<String>)

@Serializable
data class XraySecurityScanResult(val target: String, val includeLicenses: Boolean, val findings: List<String>)

@Serializable
data class AiCatalogLookupResult(
    val packageName: String,
    val ecosystem: String,
    val version: String?,
    val allowed: Boolean,
    val reason: String,
)

// JFrog tools contributed to the IDE's built-in MCP server, registered via
// the com.intellij.mcpServer.mcpToolset extension point in plugin.xml.
//
// Confirmed against the real mcpserver.jar bundled with IntelliJ 2025.2.6.2
// (package com.intellij.mcpserver, McpToolset marker interface - NOT an
// AbstractMcpTool base class, which only exists in the older, deprecated
// standalone marketplace plugin). Each public suspend fun below becomes an
// MCP tool automatically via reflection (see McpToolsProvider /
// ReflectionCallableMcpTool in mcpserver.jar); the function name (snake_case
// by platform convention, matching the bundled FileToolset/ExecutionToolset/
// CodeInsightToolset examples) becomes the tool name, and its parameters
// become the tool's JSON schema.
//
// NOT YET WIRED (spike item - see CONTRIBUTING.md "Known open risk"):
// - Each method needs the real JFrog API call using JFROG_URL/
//   JFROG_ACCESS_TOKEN (or the `jf` CLI config), matching the resolution
//   order the other JFrog plugins already use.
// - How a toolset method obtains the current Project is still unconfirmed -
//   the bundled toolsets never take it as a parameter, so it's resolved
//   ambiently (likely via coroutine context). Decompile
//   com.intellij.mcpserver.toolsets.general.CodeInsightToolset in the
//   mcpserver.jar bundled with the target IDE for the reference pattern
//   before wiring real logic here.
class JfrogToolset : McpToolset {
    suspend fun jfrog_artifactory_search(query: String, repository: String? = null): ArtifactorySearchResult {
        TODO("Wire to the JFrog Platform REST/AQL API")
    }

    suspend fun jfrog_xray_security_scan(target: String, includeLicenses: Boolean = false): XraySecurityScanResult {
        TODO("Wire to the JFrog Xray / Advanced Security API")
    }

    suspend fun jfrog_ai_catalog_lookup(
        packageName: String,
        ecosystem: String,
        version: String? = null,
    ): AiCatalogLookupResult {
        TODO("Wire to the JFrog AI Catalog / curation API")
    }
}
