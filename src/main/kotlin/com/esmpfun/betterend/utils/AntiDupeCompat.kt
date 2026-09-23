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
 * Soft integration with AntiDupePro (ADP). No compile/link dependency - ADP's
 * ownership tag is a plain PersistentDataContainer STRING (the holder's UUID)
 * under a key its admin configures (`ownership.namespace`/`ownership.key`,
 * default `antidupepro:adp_owner`), so everything here works through public
 * Bukkit API plus ADP's own config and data files.
 *
 * Why Mantle cares (see ElytraVaults):
 * - Vanilla vaults match key items with `isSameItemSameComponents`; an ADP
 *   ownership tag on the configured key stack makes the vault demand the
 *   stamping admin's exact UUID tag - no player can ever open it.
 *   [stripOwnership] removes the tag from the stamped key.
 * - A vault-dispensed elytra is a brand-new stack ADP has never seen. Pre-tagging
 *   it with the opener's UUID ([tagOwner]) makes ADP's pickup handler treat it as
 *   the player's own item: no "untracked solo pickup" warning and no immediate
 *   reconciliation pass racing the ledger credit (ELYTRA alerts at excess >= 1).
 */
object AntiDupeCompat {

    private fun adp(): Plugin? =
        Bukkit.getPluginManager().getPlugin("AntiDupePro")?.takeIf { it.isEnabled }

    val isPresent: Boolean get() = adp() != null

    /**
     * Every ownership key ADP currently recognizes: the configured primary, the
     * declared `ownership.legacy_keys`, and the key recorded in ADP's
     * `ownership-key` marker file (its self-healing rename mechanism - after a
     * rename the marker names the previous key, which is still live on items).
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

    /** Stamp [item] as owned by [owner] under ADP's primary key. No-op when ADP is absent. */
    fun tagOwner(item: ItemStack, owner: UUID) {
        val primary = ownershipKeys().firstOrNull() ?: return
        item.editMeta { meta ->
            meta.persistentDataContainer.set(primary, PersistentDataType.STRING, owner.toString())
        }
    }

    /**
     * Remove ADP ownership tags from [item]. Belt-and-braces: besides the keys ADP
     * declares, any remaining STRING entry whose value parses as a UUID is dropped
     * too (a de-branded key renamed since the marker was written still stores the
     * holder's UUID; legitimate custom-item identity tags don't).
     * @return true if anything was removed.
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
     * Whether ADP watches this material (its `materials.yml` tracked list; shulker
     * boxes are hardcoded-tracked on ADP's side). A tracked material is a bad vault
     * key: every player's copy carries their own ownership tag, so no copy matches
     * the stored key components and the vault opens for nobody.
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
