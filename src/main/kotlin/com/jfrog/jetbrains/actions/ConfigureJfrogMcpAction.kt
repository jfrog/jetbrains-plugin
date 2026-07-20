// Copyright (c) JFrog Ltd. 2026
// Licensed under the Apache License, Version 2.0
// https://www.apache.org/licenses/LICENSE-2.0

package com.jfrog.jetbrains.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import java.awt.datatransfer.StringSelection

// Fallback path (see CONTRIBUTING.md "Known open risk"): if the mcpTool
// extensions in plugin.xml turn out not to reach Junie's own tool-calling,
// this copies the JFrog MCP server JSON to the clipboard and opens Settings
// so the user can paste it into Settings | Tools | AI Assistant | MCP by
// hand - the same one-time manual step every other JFrog IDE integration
// already asks for around the platform URL.
class ConfigureJfrogMcpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val json = """
            {
              "mcpServers": {
                "jfrog": {
                  "url": "https://${'$'}{JFROG_PLATFORM_URL}/mcp"
                }
              }
            }
        """.trimIndent()

        CopyPasteManager.getInstance().setContents(StringSelection(json))

        NotificationGroupManager.getInstance()
            .getNotificationGroup("JFrog")
            .createNotification(
                "JFrog MCP server JSON copied to clipboard",
                "Paste it into Settings | Tools | AI Assistant | MCP, replacing \${JFROG_PLATFORM_URL} with your JFrog platform URL.",
                NotificationType.INFORMATION,
            )
            .notify(e.project)

        e.project?.let { ShowSettingsUtil.getInstance().showSettingsDialog(it) }
    }
}
