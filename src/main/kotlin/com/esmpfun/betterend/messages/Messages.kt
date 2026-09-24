package com.esmpfun.betterend.messages

import com.esmpfun.betterend.BetterEnd
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader

/**
 * `messages.yml`: MiniMessage templates with `{placeholder}` substitution.
 * A [Component] value is inserted as-is; anything else as text.
 */
class Messages(private val plugin: BetterEnd) {

    private val mm = MiniMessage.miniMessage()

    @Volatile
    private var messages = YamlConfiguration()

    @Volatile
    private var defaults = YamlConfiguration()

    fun load() {
        defaults = bundled()
        val file = File(plugin.dataFolder, FILE)
        if (!file.exists()) plugin.saveResource(FILE, false)
        val loaded = YamlConfiguration().apply { options().parseComments(true) }
        messages = try {
            loaded.load(file)
            sync(file, loaded)
            loaded
        } catch (e: Exception) {
            // Never save over a file we could not read: that would wipe the owner's messages.
            plugin.logger.warning(
                "messages.yml could not be read, so the built-in messages are used until it is fixed. " +
                    "Check it for TAB characters (use spaces). Details: ${e.message}"
            )
            YamlConfiguration()
        }
    }

    fun get(key: String, vararg values: Pair<String, Any?>): Component = render(template(key), values)

    /** A list key as one component per line. */
    fun lines(key: String, vararg values: Pair<String, Any?>): List<Component> =
        templates(key).map { render(it, values) }

    /** Filled in but still MiniMessage text, for APIs that parse it themselves. */
    fun raw(key: String, vararg values: Pair<String, Any?>): String = fill(template(key), values)

    fun rawLines(key: String, vararg values: Pair<String, Any?>): List<String> =
        templates(key).map { fill(it, values) }

    private fun template(key: String): String = find(messages, key) ?: find(defaults, key) ?: missing(key)

    private fun templates(key: String): List<String> =
        findList(messages, key) ?: findList(defaults, key) ?: listOf(missing(key))

    private fun find(cfg: YamlConfiguration, key: String): String? = when {
        cfg.isList(key) -> cfg.getStringList(key).joinToString("\n")
        cfg.isString(key) -> cfg.getString(key)
        else -> null
    }

    private fun findList(cfg: YamlConfiguration, key: String): List<String>? = when {
        cfg.isList(key) -> cfg.getStringList(key)
        cfg.isString(key) -> cfg.getString(key)!!.split('\n')
        else -> null
    }

    private fun missing(key: String): String {
        plugin.logger.warning("No message called '$key' exists (please report this).")
        return "[$key]"
    }

    private fun prefix(): String = find(messages, "prefix") ?: find(defaults, "prefix") ?: ""

    private fun fill(template: String, values: Array<out Pair<String, Any?>>): String {
        var raw = template.replace("{prefix}", prefix())
        for ((k, v) in values) {
            raw = raw.replace("{$k}", if (v is Component) mm.serialize(v) else v?.toString() ?: "")
        }
        return raw
    }

    // Components go in as tags so their own styling survives instead of being re-parsed.
    private fun render(template: String, values: Array<out Pair<String, Any?>>): Component {
        var raw = template.replace("{prefix}", prefix())
        val resolvers = mutableListOf<TagResolver>()
        for ((k, v) in values) {
            if (v is Component) {
                val tag = "be_" + k.lowercase().replace(Regex("[^a-z0-9_]"), "_")
                raw = raw.replace("{$k}", "<$tag>")
                resolvers += Placeholder.component(tag, v)
            } else {
                raw = raw.replace("{$k}", v?.toString() ?: "")
            }
        }
        return mm.deserialize(raw, TagResolver.resolver(resolvers))
    }

    private fun bundled(): YamlConfiguration {
        val cfg = YamlConfiguration().apply { options().parseComments(true) }
        plugin.getResource(FILE)?.use { cfg.load(InputStreamReader(it, Charsets.UTF_8)) }
        return cfg
    }

    private fun sync(file: File, user: YamlConfiguration) {
        if (defaults.getKeys(false).isEmpty()) return
        val (added, removed) = merge(user, defaults)
        if (removed.isEmpty() && added.isEmpty()) return
        try {
            user.save(file)
            if (added.isNotEmpty()) plugin.logger.info("Added ${added.size} new message(s) to messages.yml: ${added.joinToString(", ")}")
            if (removed.isNotEmpty()) plugin.logger.info("Removed ${removed.size} message(s) no longer used from messages.yml: ${removed.joinToString(", ")}")
        } catch (e: Exception) {
            plugin.logger.warning("Could not update messages.yml (${e.message}). The built-in text is used for new messages.")
        }
    }

    internal companion object {
        const val FILE = "messages.yml"

        /**
         * Adds keys [defaults] has and [user] lacks, and removes keys [defaults] no longer has.
         * Values [user] already has are never changed. Returns the added and removed keys.
         */
        fun merge(user: YamlConfiguration, defaults: YamlConfiguration): Pair<List<String>, List<String>> {
            val wanted = leaves(defaults)
            val before = user.getKeys(true)

            val removed = leaves(user).filterNot { it in wanted }
            removed.forEach { user.set(it, null) }
            // Then the sections that emptied, deepest first.
            user.getKeys(true)
                .filter { user.isConfigurationSection(it) && !defaults.isConfigurationSection(it) }
                .sortedByDescending { it.count { c -> c == '.' } }
                .forEach { path -> if (user.getConfigurationSection(path)?.getKeys(false).isNullOrEmpty()) user.set(path, null) }

            // A key that changed from a section to a single message counts as missing.
            val added = wanted.filter { !user.contains(it) || user.isConfigurationSection(it) }
            added.forEach { user.set(it, defaults.get(it)) }
            // New keys and the new sections holding them arrive with their notes.
            defaults.getKeys(true).filter { it !in before }.forEach { path ->
                user.setComments(path, defaults.getComments(path))
                user.setInlineComments(path, defaults.getInlineComments(path))
            }
            return added to removed
        }

        private fun leaves(cfg: YamlConfiguration): Set<String> =
            cfg.getKeys(true).filterTo(LinkedHashSet()) { !cfg.isConfigurationSection(it) }
    }
}
