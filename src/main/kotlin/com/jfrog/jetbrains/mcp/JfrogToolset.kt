// Copyright (c) JFrog Ltd. 2026
// Licensed under the Apache License, Version 2.0
// https://www.apache.org/licenses/LICENSE-2.0

package com.jfrog.jetbrains.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
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
// Registration rule (confirmed against the real mcpserver.jar bundled with
// IntelliJ 2025.2.6.2): only methods annotated with @McpTool are exposed -
// see ToolsetReflection_utilKt.getImplementedMethods, which filters on that
// annotation. A bare public suspend fun is NOT enough. @McpDescription on the
// method and on each parameter feeds the tool/parameter descriptions in the
// generated JSON schema. The function name becomes the tool name (snake_case
// by platform convention, matching the bundled FileToolset/ExecutionToolset/
// CodeInsightToolset examples).
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
    @McpTool
    @McpDescription("Search JFrog Artifactory for artifacts matching a query, optionally scoped to a repository.")
    suspend fun jfrog_artifactory_search(
        @McpDescription("Search term - artifact name or path fragment") query: String,
        @McpDescription("Optional repository key to scope the search") repository: String? = null,
    ): ArtifactorySearchResult {
        TODO("Wire to the JFrog Platform REST/AQL API")
    }

    @McpTool
    @McpDescription("Run a JFrog Xray / Advanced Security scan on a target and return its findings.")
    suspend fun jfrog_xray_security_scan(
        @McpDescription("Scan target - build name, artifact path, or repository key") target: String,
        @McpDescription("Include license findings in addition to vulnerabilities") includeLicenses: Boolean = false,
    ): XraySecurityScanResult {
        TODO("Wire to the JFrog Xray / Advanced Security API")
    }

    @McpTool
    @McpDescription("Check whether a package version is allowed via the JFrog AI Catalog / curation policy.")
    suspend fun jfrog_ai_catalog_lookup(
        @McpDescription("Package name to look up") packageName: String,
        @McpDescription("Package ecosystem, e.g. npm, pypi, maven, go") ecosystem: String,
        @McpDescription("Optional specific version to check") version: String? = null,
    ): AiCatalogLookupResult {
        TODO("Wire to the JFrog AI Catalog / curation API")
    }
}
