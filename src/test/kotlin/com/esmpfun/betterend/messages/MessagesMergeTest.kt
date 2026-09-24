package com.esmpfun.betterend.messages

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessagesMergeTest {

    private fun yaml(text: String) = YamlConfiguration().apply {
        options().parseComments(true)
        loadFromString(text)
    }

    private fun bundled() = YamlConfiguration().apply {
        options().parseComments(true)
        load(javaClass.classLoader.getResourceAsStream("messages.yml")!!.reader())
    }

    @Test
    fun `adds new keys, removes retired ones, keeps edited text`() {
        val user = yaml(
            """
            prefix: "<red>[Mine]</red> "
            command:
              reload-done: "<gold>Reloaded!"
              retired: "old"
            gone:
              deep:
                key: "x"
            """.trimIndent()
        )
        val defaults = yaml(
            """
            prefix: "<light_purple>[BetterEndCities]</light_purple> "
            command:
              reload-done: "<green>Reloaded."
              # Explains the new message.
              new-one: "<green>New"
            menu:
              body:
                - "a"
                - "b"
            """.trimIndent()
        )

        val (added, removed) = Messages.merge(user, defaults)

        assertEquals(listOf("command.new-one", "menu.body"), added)
        assertEquals(listOf("command.retired", "gone.deep.key"), removed)
        assertEquals("<red>[Mine]</red> ", user.getString("prefix"))
        assertEquals("<gold>Reloaded!", user.getString("command.reload-done"))
        assertEquals(listOf("a", "b"), user.getStringList("menu.body"))
        assertFalse(user.contains("gone"))
        assertTrue(user.saveToString().contains("# Explains the new message."))
    }

    @Test
    fun `a key that became a section is replaced`() {
        val user = yaml("elytra:\n  hint: \"old\"\n")
        val defaults = yaml("elytra:\n  hint:\n    free: \"a\"\n    cost: \"b\"\n")

        val (added, removed) = Messages.merge(user, defaults)

        assertEquals(listOf("elytra.hint"), removed)
        assertEquals(listOf("elytra.hint.free", "elytra.hint.cost"), added)
    }

    @Test
    fun `an up to date file is left alone`() {
        val user = bundled()
        val (added, removed) = Messages.merge(user, bundled())
        assertTrue(added.isEmpty(), "added $added")
        assertTrue(removed.isEmpty(), "removed $removed")
    }

    @Test
    fun `bundled keys are all plain words`() {
        val cfg = bundled()
        // YAML reads bare off/on/yes/no as true or false, which breaks the lookup.
        assertTrue(cfg.isString("menu.updates.modes.off"))
        assertFalse(cfg.getKeys(true).any { it.split('.').any { part -> part == "true" || part == "false" } })
    }

    @Test
    fun `help keeps argument names like id visible`() {
        val mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
        val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        val line = bundled().getStringList("command.help").first { "resetloot" in it }
        assertEquals("/betterend resetloot <id> <player> - let one player loot the city fresh", plain.serialize(mm.deserialize(line)))
    }
}
