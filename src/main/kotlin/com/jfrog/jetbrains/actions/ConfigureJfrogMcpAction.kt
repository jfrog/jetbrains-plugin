// Copyright (c) JFrog Ltd. 2026
// Licensed under the Apache License, Version 2.0
// https://www.apache.org/licenses/LICENSE-2.0

package com.jfrog.jetbrains.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.util.function.Consumer
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.JLabel

// Fallback path (see CONTRIBUTING.md "Known open risk"): if the mcpTool
// extensions in plugin.xml turn out not to reach Junie's own tool-calling,
// this copies an MCP server entry to the clipboard and opens Settings so
// the user can paste it into Settings | Tools | AI Assistant | Model
// Context Protocol (MCP) by hand. That page (Configurable id "ml.llm.mcp",
// confirmed by decompiling com.intellij.ml.llm.mcp.client.settings.
// McpConfigurable.getId() in the bundled AI Assistant plugin's ml-llm.jar)
// belongs to the AI Assistant/Junie plugin, not the separate
// com.intellij.mcpServer plugin (Settings | Tools | MCP Server) that this
// plugin's own mcpTool extensions register against - those are two
// different, unrelated MCP integrations that happen to sit under the same
// Tools settings group.
//
// We navigate with the (Project, Predicate, Consumer) overload rather than
// the (Project, String) one, which matches by *display name*, not id
// (confirmed by decompiling ShowSettingsUtilImpl.showSettingsDialog(Project,
// String), which calls findPreselectedByDisplayName) - passing "ml.llm.mcp"
// there never matched anything and silently fell back to the top of the
// settings tree. The predicate overload throws IllegalStateException if
// nothing matches instead of failing silently, so a missing AI Assistant
// plugin is handled explicitly below rather than degrading to a no-op.
//
// JFrog's MCP server is remote (see github.com/jfrog/jfrog-mcp-server),
// so its documented client config is a plain "url" entry - no local
// binary. This settings page's server entries only support
// command/args/env, though: confirmed by decompiling
// mcp/client/settings/McpServerBean.class (no url field anywhere) and by
// the literal string ml.llm.mcp.server.json.config.invalid = "Failed to
// parse configuration" in the plugin's own McpBundle.properties. There is
// no way to make this JSON paste-able on this page in this IDE build; it
// is JFrog's real format, kept as-is for use in a client that does
// support remote/url servers, and the dialog below says so explicitly
// rather than sending the user looking for a paste option that isn't there.
class ConfigureJfrogMcpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val rawUrl = System.getenv("JFROG_PLATFORM_URL")?.trimEnd('/')
        val platformUrl = when {
            rawUrl == null -> "<JFROG_PLATFORM_URL>"
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
            else -> "https://$rawUrl"
        }
        val json = """
            {
              "mcpServers": {
                "jfrog": {
                  "url": "$platformUrl/mcp"
                }
              }
            }
        """.trimIndent()

        CopyPasteManager.getInstance().setContents(StringSelection(json))

        val instructions = if (rawUrl == null) {
            "Set the JFROG_PLATFORM_URL environment variable to your JFrog platform URL and rerun this action, or replace <JFROG_PLATFORM_URL> yourself before pasting."
        } else {
            "The URL was filled in from your JFROG_PLATFORM_URL environment variable."
        }

        JfrogMcpConfigDialog(json, instructions).show()

        e.project?.let { project ->
            try {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    project,
                    Predicate<Configurable> { it is ConfigurableWithId && it.id == "ml.llm.mcp" },
                    Consumer<Configurable> {},
                )
            } catch (_: IllegalStateException) {
                // AI Assistant plugin not installed - the config is already
                // on the clipboard and explained in the dialog above.
            }
        }
    }
}

private class JfrogMcpConfigDialog(
    private val json: String,
    private val instructions: String,
) : DialogWrapper(null, false) {
    init {
        title = "JFrog MCP Server Config Copied to Clipboard"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val message = JLabel(
            "<html>This is JFrog's remote MCP server config. Settings | Tools | AI Assistant | " +
                "Model Context Protocol (MCP) only accepts local command-based servers, so pasting " +
                "it there via + | As JSON will not launch anything - use this JSON in an MCP client " +
                "that supports remote/url servers instead.<br><br>$instructions</html>",
        )
        val textArea = JBTextArea(json).apply {
            isEditable = false
            lineWrap = false
        }
        val scrollPane = JBScrollPane(textArea).apply {
            preferredSize = Dimension(520, 160)
        }

        val panel = com.intellij.util.ui.FormBuilder.createFormBuilder()
            .addComponent(message)
            .addComponentFillVertically(scrollPane, JBUI.scale(8))
            .panel
        panel.preferredSize = Dimension(560, 320)
        return panel
    }
}
