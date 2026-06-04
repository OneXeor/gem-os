package com.onexeor.gemos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigLoaderTest {
    @Test
    fun loadsExampleConfig() {
        val cfg = ConfigLoader.load(".")

        assertEquals("Gem", cfg.identity.gem.name)
        assertEquals("litellm", cfg.providers.providers.chat.default)
        assertEquals("codex", cfg.providers.providers.codeAgent.default)
        assertTrue(cfg.projects.projects.any { it.id == "aso-fabric" })
    }
}

