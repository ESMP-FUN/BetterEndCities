package com.esmpfun.betterend.utils

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.UUID

/**
 * Soft integration with Better Anti-Dupe, with no compile-time dependency.
 *
 * Its ownership tag is a plain PDC string (the holder's UUID) under a key its
 * config names, so everything here goes through Bukkit API and its own files.
 * A claimed elytra is pre-stamped with the claimer, so it counts as their own
 * item instead of an untracked pickup.
 */
object AntiDupeCompat {

    // Current name first; AntiDupePro is what it was called before.
    private val PLUGIN_NAMES = listOf("BetterAntiDupe", "AntiDupePro")

    private fun adp(): Plugin? = PLUGIN_NAMES.firstNotNullOfOrNull { name ->
        Bukkit.getPluginManager().getPlugin(name)?.takeIf { it.isEnabled }
    }

    val isPresent: Boolean get() = adp() != null

    /**
     * Every ownership key it currently recognises: the configured primary, the
     * declared legacy keys, and the key in its `ownership-key` marker file
     * (the previous key after a rename, still live on older items).
     */
    fun ownershipKeys(): List<NamespacedKey> {
        val plugin = adp() ?: return emptyList()
        val cfg = plugin.config
        val defaultNs = plugin.name.lowercase()

        val keys = LinkedHashSet<NamespacedKey>()
        val ns = (cfg.getString("ownership.namespace") ?: defaultNs).lowercase().trim()
        val key = (cfg.getString("ownership.key") ?: "adp_owner").lowercase().trim()
        (NamespacedKey.fromString("$ns:$key")?.takeIf { ns != "minecraft" }
            ?: NamespacedKey.fromString("$defaultNs:adp_owner"))?.let { keys.add(it) }

        for (raw in cfg.getStringList("ownership.legacy_keys")) {
            NamespacedKey.fromString(raw.lowercase().trim())?.let { keys.add(it) }
        }
        runCatching {
            val marker = File(plugin.dataFolder, "ownership-key")
            if (marker.isFile) NamespacedKey.fromString(marker.readText().trim())?.let { keys.add(it) }
        }
        return keys.toList()
    }

    /** Stamps [item] as owned by [owner] under the primary key. No-op when the plugin is absent. */
    fun tagOwner(item: ItemStack, owner: UUID) {
        val primary = ownershipKeys().firstOrNull() ?: return
        item.editMeta { meta ->
            meta.persistentDataContainer.set(primary, PersistentDataType.STRING, owner.toString())
        }
    }

    /**
     * Removes ownership tags from [item]. Any other non-minecraft STRING entry
     * holding a UUID goes too, which catches a key renamed since the marker was
     * written. Returns true if anything was removed.
     */
    fun stripOwnership(item: ItemStack): Boolean {
        if (!isPresent) return false
        val declared = ownershipKeys().toSet()
        var removed = false
        item.editMeta { meta ->
            val pdc = meta.persistentDataContainer
            for (k in pdc.keys.toList()) {
                val hit = k in declared || (k.namespace != NamespacedKey.MINECRAFT &&
                    pdc.get(k, PersistentDataType.STRING)?.let(::isUuid) == true)
                if (hit) {
                    pdc.remove(k)
                    removed = true
                }
            }
        }
        return removed
    }

    /**
     * Whether it tracks [material]. Every player's copy of a tracked item carries
     * their own ownership tag, so a tracked item can never match a cost item.
     */
    fun isTrackedMaterial(material: Material): Boolean {
        val plugin = adp() ?: return false
        if (material.name.endsWith("SHULKER_BOX")) return true
        return runCatching {
            val file = File(plugin.dataFolder, "materials.yml")
            if (!file.isFile) return false
            YamlConfiguration.loadConfiguration(file)
                .getStringList("tracked_materials")
                .any { Material.matchMaterial(it) == material }
        }.getOrDefault(false)
    }

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess
}
