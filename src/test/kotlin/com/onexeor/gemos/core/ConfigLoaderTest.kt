package com.onexeor.gemos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigLoaderTest {
    @Test
    fun loadsExampleConfig() {
        val cfg = ConfigLoader.load(".")

        assertEquals("Gem", cfg.identity.gem.name)
        assertEquals("http://bge-m3:7997", cfg.settings.embeddingsBaseUrl)
        assertEquals("BAAI/bge-m3", cfg.settings.embeddingsModel)
        assertEquals(1024, cfg.settings.embeddingsVectorSize)
        assertEquals("litellm", cfg.providers.providers.chat.default)
        assertEquals("codex", cfg.providers.providers.codeAgent.default)
        assertTrue(cfg.projects.projects.any { it.id == "aso-fabric" })
    }
}
