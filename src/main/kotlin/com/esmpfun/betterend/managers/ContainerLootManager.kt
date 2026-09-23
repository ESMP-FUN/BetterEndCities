package com.esmpfun.betterend.managers

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bukkit.inventory.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player End City container loot (Lootr-style). Ported from
 * TrialChamberPro's ContainerLootManager.
 *
 * Every player gets a private copy of a city container's contents, stored one
 * row per (container position, player) in `player_container_loot`. The real
 * block's inventory is never modified - it stays the pristine template every new
 * player's copy is cloned from. The shared template lives in `container_template`
 * and persists across resets so op edits stick.
 *
 * Lifecycle: per-player copies are cleared per city on reset ([clearCity]) and
 * cascade-deleted with the city row.
 *
 * Contents encoding: base64 of a length-prefixed sequence of
 * `ItemStack.serializeAsBytes()` blobs (slot-faithful; -1 marks an empty slot).
 */
class ContainerLootManager(private val plugin: BetterEnd) {

    data class ContainerPos(val x: Int, val y: Int, val z: Int)

    private data class CopyKey(val cityId: Int, val pos: ContainerPos, val player: UUID)
    private data class TemplateKey(val cityId: Int, val pos: ContainerPos)

    /**
     * Closed inventories whose database write hasn't landed yet. Reads check
     * these first, so reopening a chest straight after closing it can never
     * serve the contents from before the close (which would hand the same
     * loot out twice). Whatever is still here on shutdown is written by
     * [flushPending].
     */
    private val pendingCopies = ConcurrentHashMap<CopyKey, Array<ItemStack?>>()
    private val pendingTemplates = ConcurrentHashMap<TemplateKey, Array<ItemStack?>>()

    /** Saves a closed private copy in the background. [contents] must not be touched afterwards. */
    fun queueSaveContents(cityId: Int, pos: ContainerPos, player: UUID, contents: Array<ItemStack?>) {
        val key = CopyKey(cityId, pos, player)
        pendingCopies[key] = contents
        plugin.launchAsync {
            if (saveContents(cityId, pos, player, contents)) pendingCopies.remove(key, contents)
        }
    }

    /** Saves an edited template in the background. [contents] must not be touched afterwards. */
    fun queueUpdateTemplate(cityId: Int, pos: ContainerPos, contents: Array<ItemStack?>) {
        val key = TemplateKey(cityId, pos)
        pendingTemplates[key] = contents
        plugin.launchAsync {
            if (updateTemplateContents(cityId, pos, contents)) pendingTemplates.remove(key, contents)
        }
    }

    /** Writes every save still waiting, blocking until done. For shutdown, after the pool's last use by anything else. */
    fun flushPending() {
        if (pendingCopies.isEmpty() && pendingTemplates.isEmpty()) return
        runBlocking {
            for ((k, contents) in pendingCopies) {
                if (saveContents(k.cityId, k.pos, k.player, contents)) pendingCopies.remove(k, contents)
            }
            for ((k, contents) in pendingTemplates) {
                if (updateTemplateContents(k.cityId, k.pos, contents)) pendingTemplates.remove(k, contents)
            }
        }
        val lost = pendingCopies.size + pendingTemplates.size
        if (lost > 0) plugin.logger.warning("[ContainerLoot] $lost loot inventories could not be saved on shutdown.")
    }

    private fun Array<ItemStack?>.deepCopy(): Array<ItemStack?> = Array(size) { this[it]?.clone() }

    /** A player's private contents for a container, or null on first open. */
    suspend fun loadContents(
        cityId: Int,
        pos: ContainerPos,
        player: UUID
    ): Array<ItemStack?>? {
        pendingCopies[CopyKey(cityId, pos, player)]?.let { return it.deepCopy() }
        return loadStoredContents(cityId, pos, player)
    }

    private suspend fun loadStoredContents(
        cityId: Int,
        pos: ContainerPos,
        player: UUID
    ): Array<ItemStack?>? = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT contents FROM player_container_loot WHERE city_id = ? AND x = ? AND y = ? AND z = ? AND player_uuid = ?"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setInt(2, pos.x); stmt.setInt(3, pos.y); stmt.setInt(4, pos.z)
                    stmt.setString(5, player.toString())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) decodeContents(rs.getString("contents")) else null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Load failed (${pos.x},${pos.y},${pos.z}/$player): ${e.message}")
            com.esmpfun.betterend.integrations.MetricsService.reportHandled(e, "container-loot-load")
            null
        }
    }

    /** Persists a player's private contents for a container (upsert). False when the write failed. */
    private suspend fun saveContents(
        cityId: Int,
        pos: ContainerPos,
        player: UUID,
        contents: Array<ItemStack?>
    ): Boolean = withContext(Dispatchers.IO) {
        val encoded = encodeContents(contents)
        val sql = if (plugin.databaseManager.databaseType == DatabaseManager.DatabaseType.MYSQL) {
            """
            INSERT INTO player_container_loot (city_id, x, y, z, player_uuid, contents, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE contents = VALUES(contents), updated_at = VALUES(updated_at)
            """.trimIndent()
        } else {
            """
            INSERT INTO player_container_loot (city_id, x, y, z, player_uuid, contents, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(city_id, x, y, z, player_uuid)
            DO UPDATE SET contents = excluded.contents, updated_at = excluded.updated_at
            """.trimIndent()
        }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setInt(2, pos.x); stmt.setInt(3, pos.y); stmt.setInt(4, pos.z)
                    stmt.setString(5, player.toString())
                    stmt.setString(6, encoded)
                    stmt.setLong(7, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
            }
            true
        } catch (e: Exception) {
            // Kept in pendingCopies, so a reopen still sees it and shutdown retries it.
            plugin.logger.warning("[ContainerLoot] Save failed (${pos.x},${pos.y},${pos.z}/$player): ${e.message}")
            com.esmpfun.betterend.integrations.MetricsService.reportHandled(e, "container-loot-save")
            false
        }
    }

    /** The shared template for a container, or null when not yet materialized. */
    suspend fun loadTemplate(
        cityId: Int,
        pos: ContainerPos
    ): Array<ItemStack?>? {
        pendingTemplates[TemplateKey(cityId, pos)]?.let { return it.deepCopy() }
        return loadStoredTemplate(cityId, pos)
    }

    private suspend fun loadStoredTemplate(
        cityId: Int,
        pos: ContainerPos
    ): Array<ItemStack?>? = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT contents FROM container_template WHERE city_id = ? AND x = ? AND y = ? AND z = ?"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setInt(2, pos.x); stmt.setInt(3, pos.y); stmt.setInt(4, pos.z)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) decodeContents(rs.getString("contents")) else null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Template load failed (${pos.x},${pos.y},${pos.z}): ${e.message}")
            null
        }
    }

    /** Persists the shared template for a container (upsert). */
    suspend fun saveTemplate(
        cityId: Int,
        pos: ContainerPos,
        contents: Array<ItemStack?>,
        material: org.bukkit.Material
    ) = withContext(Dispatchers.IO) {
        val encoded = encodeContents(contents)
        val sql = if (plugin.databaseManager.databaseType == DatabaseManager.DatabaseType.MYSQL) {
            """
            INSERT INTO container_template (city_id, x, y, z, contents, material, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE contents = VALUES(contents), material = VALUES(material), updated_at = VALUES(updated_at)
            """.trimIndent()
        } else {
            """
            INSERT INTO container_template (city_id, x, y, z, contents, material, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(city_id, x, y, z)
            DO UPDATE SET contents = excluded.contents, material = excluded.material, updated_at = excluded.updated_at
            """.trimIndent()
        }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setInt(2, pos.x); stmt.setInt(3, pos.y); stmt.setInt(4, pos.z)
                    stmt.setString(5, encoded)
                    stmt.setString(6, material.name)
                    stmt.setLong(7, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Template save failed (${pos.x},${pos.y},${pos.z}): ${e.message}")
        }
    }

    /** Updates only the CONTENTS of an existing template (op edit), preserving its icon. False when the write failed. */
    private suspend fun updateTemplateContents(
        cityId: Int,
        pos: ContainerPos,
        contents: Array<ItemStack?>
    ): Boolean = withContext(Dispatchers.IO) {
        val encoded = encodeContents(contents)
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "UPDATE container_template SET contents = ?, updated_at = ? WHERE city_id = ? AND x = ? AND y = ? AND z = ?"
                ).use { stmt ->
                    stmt.setString(1, encoded)
                    stmt.setLong(2, System.currentTimeMillis())
                    stmt.setInt(3, cityId)
                    stmt.setInt(4, pos.x); stmt.setInt(5, pos.y); stmt.setInt(6, pos.z)
                    stmt.executeUpdate()
                }
            }
            true
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Template content update failed (${pos.x},${pos.y},${pos.z}): ${e.message}")
            false
        }
    }

    /** One stored template: its position, decoded contents, and container icon. */
    data class TemplateRow(
        val pos: ContainerPos,
        val contents: Array<ItemStack?>,
        val material: org.bukkit.Material
    )

    /** Lists every materialized template for a city. */
    suspend fun listTemplates(cityId: Int): List<TemplateRow> = withContext(Dispatchers.IO) {
        val out = mutableListOf<TemplateRow>()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT x, y, z, contents, material FROM container_template WHERE city_id = ? ORDER BY x, y, z"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val pos = ContainerPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                            val contents = decodeContents(rs.getString("contents")) ?: arrayOfNulls(0)
                            val material = runCatching { org.bukkit.Material.valueOf(rs.getString("material")) }
                                .getOrDefault(org.bukkit.Material.CHEST)
                            out.add(TemplateRow(pos, contents, material))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] listTemplates failed for city $cityId: ${e.message}")
        }
        out
    }

    /** Whether a template already exists for a container position. */
    suspend fun hasTemplate(cityId: Int, pos: ContainerPos): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT 1 FROM container_template WHERE city_id = ? AND x = ? AND y = ? AND z = ?"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setInt(2, pos.x); stmt.setInt(3, pos.y); stmt.setInt(4, pos.z)
                    stmt.executeQuery().use { rs -> rs.next() }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] hasTemplate failed: ${e.message}")
            false
        }
    }

    /** Counts a city's per-player container copies. */
    suspend fun countPlayerCopies(cityId: Int): Int = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT COUNT(*) FROM player_container_loot WHERE city_id = ?"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] countPlayerCopies failed: ${e.message}")
            0
        }
    }

    /** Deletes every shared template for a city (they re-materialize on next access). */
    suspend fun clearTemplates(cityId: Int): Int = withContext(Dispatchers.IO) {
        pendingTemplates.keys.removeIf { it.cityId == cityId }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM container_template WHERE city_id = ?").use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] clearTemplates failed for city $cityId: ${e.message}")
            0
        }
    }

    /** Deletes a single container's template. */
    suspend fun deleteTemplate(cityId: Int, pos: ContainerPos): Boolean = withContext(Dispatchers.IO) {
        pendingTemplates.remove(TemplateKey(cityId, pos))
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "DELETE FROM container_template WHERE city_id = ? AND x = ? AND y = ? AND z = ?"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setInt(2, pos.x); stmt.setInt(3, pos.y); stmt.setInt(4, pos.z)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] deleteTemplate failed: ${e.message}")
            false
        }
    }

    /**
     * Drops every player's container copies for a city - fresh loot for everyone
     * after a reset. Shared templates are intentionally KEPT (op edits persist
     * across resets). Returns the number of copies removed.
     */
    suspend fun clearCity(cityId: Int): Int = withContext(Dispatchers.IO) {
        pendingCopies.keys.removeIf { it.cityId == cityId }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM player_container_loot WHERE city_id = ?").use { stmt ->
                    stmt.setInt(1, cityId)
                    val n = stmt.executeUpdate()
                    if (n > 0 && plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("[ContainerLoot] Cleared $n per-player container copies for city $cityId")
                    }
                    n
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] Clear failed for city $cityId: ${e.message}")
            0
        }
    }

    /**
     * Drops one player's container copies for a city, so they re-roll from the
     * template on their next open - the "reset this player's loot" admin action.
     * Returns rows removed.
     */
    suspend fun clearPlayer(cityId: Int, player: UUID): Int = withContext(Dispatchers.IO) {
        pendingCopies.keys.removeIf { it.cityId == cityId && it.player == player }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "DELETE FROM player_container_loot WHERE city_id = ? AND player_uuid = ?"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setString(2, player.toString())
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] clearPlayer failed (city $cityId / $player): ${e.message}")
            0
        }
    }

    /** One player's container copies in a city: position + decoded contents. */
    data class PlayerCopy(val pos: ContainerPos, val contents: Array<ItemStack?>)

    /** Lists a player's per-container copies for a city (for the GUI "what they looted" view). */
    /** Forgets a deleted city's unsaved inventories; its rows already went with the city row. */
    fun dropCity(cityId: Int) {
        pendingCopies.keys.removeIf { it.cityId == cityId }
        pendingTemplates.keys.removeIf { it.cityId == cityId }
    }

    suspend fun listPlayerCopies(cityId: Int, player: UUID): List<PlayerCopy> = withContext(Dispatchers.IO) {
        val out = mutableListOf<PlayerCopy>()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT x, y, z, contents FROM player_container_loot WHERE city_id = ? AND player_uuid = ? ORDER BY x, y, z"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setString(2, player.toString())
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val pos = ContainerPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                            val contents = decodeContents(rs.getString("contents")) ?: arrayOfNulls(0)
                            out.add(PlayerCopy(pos, contents))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ContainerLoot] listPlayerCopies failed (city $cityId / $player): ${e.message}")
        }
        out
    }

    // ==== Encoding ====

    fun encodeContents(contents: Array<ItemStack?>): String {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(contents.size)
            for (item in contents) {
                if (item == null || item.type.isAir) {
                    out.writeInt(-1)
                } else {
                    val bytes = item.serializeAsBytes()
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
            }
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    fun decodeContents(encoded: String): Array<ItemStack?>? = try {
        val bytes = Base64.getDecoder().decode(encoded)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val size = input.readInt()
            require(size in 0..128) { "implausible container size $size" }
            Array(size) {
                val len = input.readInt()
                if (len < 0) null
                else {
                    val buf = ByteArray(len)
                    input.readFully(buf)
                    ItemStack.deserializeBytes(buf)
                }
            }
        }
    } catch (e: Exception) {
        plugin.logger.warning("[ContainerLoot] Corrupt contents row ignored: ${e.message}")
        null
    }
}
