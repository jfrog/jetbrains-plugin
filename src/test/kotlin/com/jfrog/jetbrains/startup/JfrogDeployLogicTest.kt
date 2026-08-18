// Copyright (c) JFrog Ltd. 2026
// Licensed under the Apache License, Version 2.0
// https://www.apache.org/licenses/LICENSE-2.0

package com.jfrog.jetbrains.startup

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JfrogDeployLogicTest {

    // --- normalizeHost ---

    @Test
    fun normalizeHost_addsHttpsAndTrimsSlash() {
        assertEquals("https://acme.jfrog.io", JfrogDeployLogic.normalizeHost("acme.jfrog.io/"))
        assertEquals("https://acme.jfrog.io", JfrogDeployLogic.normalizeHost("  acme.jfrog.io  "))
    }

    @Test
    fun normalizeHost_keepsExistingScheme() {
        assertEquals("http://localhost:8081", JfrogDeployLogic.normalizeHost("http://localhost:8081"))
        assertEquals("https://acme.jfrog.io", JfrogDeployLogic.normalizeHost("https://acme.jfrog.io"))
    }

    @Test
    fun normalizeHost_nullOnBlank() {
        assertNull(JfrogDeployLogic.normalizeHost(null))
        assertNull(JfrogDeployLogic.normalizeHost("   "))
    }

    // --- selectHostFromCliConfig ---

    @Test
    fun selectHost_singleServerUsedEvenWithoutDefault() {
        val json = """{"servers":[{"url":"https://only.jfrog.io/"}]}"""
        assertEquals("https://only.jfrog.io", JfrogDeployLogic.selectHostFromCliConfig(json))
    }

    @Test
    fun selectHost_multipleServersPrefersDefault() {
        val json = """{"servers":[
            {"url":"https://a.jfrog.io"},
            {"url":"https://b.jfrog.io","isDefault":true}
        ]}"""
        assertEquals("https://b.jfrog.io", JfrogDeployLogic.selectHostFromCliConfig(json))
    }

    @Test
    fun selectHost_multipleServersNoDefaultDoesNotGuess() {
        val json = """{"servers":[
            {"url":"https://a.jfrog.io"},
            {"url":"https://b.jfrog.io"}
        ]}"""
        assertNull(JfrogDeployLogic.selectHostFromCliConfig(json))
    }

    @Test
    fun selectHost_emptyOrInvalid() {
        assertNull(JfrogDeployLogic.selectHostFromCliConfig("""{"servers":[]}"""))
        assertNull(JfrogDeployLogic.selectHostFromCliConfig("""{}"""))
        assertNull(JfrogDeployLogic.selectHostFromCliConfig("not json"))
    }

    @Test
    fun selectHost_toleratesJsonNullFields() {
        // isDefault: null and url: null must not throw.
        val json = """{"servers":[
            {"url":null,"isDefault":null},
            {"url":"https://b.jfrog.io","isDefault":true}
        ]}"""
        assertEquals("https://b.jfrog.io", JfrogDeployLogic.selectHostFromCliConfig(json))
    }

    // --- mergeMcpJson ---

    @Test
    fun merge_createsEntryWhenFileMissing() {
        val out = JfrogDeployLogic.mergeMcpJson(null, "https://acme.jfrog.io/mcp", urlIsPlaceholder = false)!!
        val jfrog = JsonParser.parseString(out).asJsonObject
            .getAsJsonObject("mcpServers").getAsJsonObject("jfrog")
        assertEquals("https://acme.jfrog.io/mcp", jfrog.get("url").asString)
    }

    @Test
    fun merge_preservesOtherServers() {
        val current = """{"mcpServers":{"other":{"command":"npx","args":["x"]}}}"""
        val out = JfrogDeployLogic.mergeMcpJson(current, "https://acme.jfrog.io/mcp", urlIsPlaceholder = false)!!
        val servers = JsonParser.parseString(out).asJsonObject.getAsJsonObject("mcpServers")
        assertTrue(servers.has("other"))
        assertTrue(servers.has("jfrog"))
    }

    @Test
    fun merge_placeholderDoesNotStompExistingEntry() {
        val current = """{"mcpServers":{"jfrog":{"url":"https://acme.jfrog.io/mcp"}}}"""
        val out = JfrogDeployLogic.mergeMcpJson(current, "https://<JFROG_PLATFORM_URL>/mcp", urlIsPlaceholder = true)
        assertNull(out)
    }

    @Test
    fun merge_noChangeWhenUrlAlreadyCorrect() {
        val current = """{"mcpServers":{"jfrog":{"url":"https://acme.jfrog.io/mcp"}}}"""
        val out = JfrogDeployLogic.mergeMcpJson(current, "https://acme.jfrog.io/mcp", urlIsPlaceholder = false)
        assertNull(out)
    }

    @Test
    fun merge_unparseableFileIsNotClobbered() {
        val out = JfrogDeployLogic.mergeMcpJson("{ this is not json", "https://acme.jfrog.io/mcp", urlIsPlaceholder = false)
        assertNull(out)
    }

    @Test
    fun merge_toleratesJsonNullUrlOnExistingEntry() {
        // Existing jfrog.url is JSON null -> must not throw, and should update.
        val current = """{"mcpServers":{"jfrog":{"url":null}}}"""
        val out = JfrogDeployLogic.mergeMcpJson(current, "https://acme.jfrog.io/mcp", urlIsPlaceholder = false)!!
        val jfrog = JsonParser.parseString(out).asJsonObject
            .getAsJsonObject("mcpServers").getAsJsonObject("jfrog")
        assertEquals("https://acme.jfrog.io/mcp", jfrog.get("url").asString)
    }
}
