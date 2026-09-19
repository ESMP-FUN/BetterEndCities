package com.esmpfun.betterend.managers

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Elytra claim bookkeeping. Fully preloaded into memory (claim volume is
 * players × ships — tiny), because the punch handler must answer
 * "has this player claimed?" synchronously to cancel the event, with the DB
 * only written behind the cache.
 *
 * Claim modes (config `elytra.claim-mode`):
 *  - `per-ship`    — one claim per (city, player), ever.
 *  - `per-refresh` — a claim goes stale when the city's loot cycle rolls over
 *                    (claimed_at < the city's current cycle start).
 *  - `global`      — one claim per player across all cities.
 */
class ElytraClaimManager(private val plugin: BetterEnd) {

    enum class ClaimMode(val key: String, val label: String) {
        PER_SHIP("per-ship", "Once per ship"),
        PER_REFRESH("per-refresh", "Again after each loot refresh"),
        GLOBAL("global", "Once per player, total");

        companion object {
            fun fromConfig(raw: String?): ClaimMode =
                entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: PER_SHIP
        }
    }

    /** cityId -> (player -> claimed_at). */
    private val claims = ConcurrentHashMap<Int, ConcurrentHashMap<UUID, Long>>()

    fun mode(): ClaimMode = ClaimMode.fromConfig(plugin.config.getString("elytra.claim-mode"))

    /** Loads every claim into the cache. Call once at startup. */
    suspend fun preload() = withContext(Dispatchers.IO) {
        claims.clear()
        plugin.databaseManager.connection.use { conn ->
            conn.prepareStatement("SELECT city_id, player_uuid, claimed_at FROM elytra_claims").use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val cityId = rs.getInt("city_id")
                        val uuid = runCatching { UUID.fromString(rs.getString("player_uuid")) }.getOrNull() ?: continue
                        claims.getOrPut(cityId) { ConcurrentHashMap() }[uuid] = rs.getLong("claimed_at")
                    }
                }
            }
        }
        val total = claims.values.sumOf { it.size }
        if (total > 0) plugin.logger.info("Loaded $total elytra claim${if (total == 1) "" else "s"} into cache")
    }

    /**
     * Whether [player] is currently blocked from claiming at [cityId] under the
     * configured mode. Synchronous — reads the in-memory cache only.
     */
    fun hasClaimed(cityId: Int, player: UUID): Boolean = when (mode()) {
        ClaimMode.PER_SHIP -> claims[cityId]?.containsKey(player) == true
        ClaimMode.PER_REFRESH -> {
            val at = claims[cityId]?.get(player)
            // No cycle yet (0) = nothing has expired claims, so any claim holds.
            at != null && at >= plugin.cityManager.cycleStart(cityId)
        }
        ClaimMode.GLOBAL -> claims.values.any { it.containsKey(player) }
    }

    /** Records a claim (cache now, DB behind it — upsert so re-claims after a
     *  refresh just move claimed_at forward). */
    fun record(cityId: Int, player: UUID) {
        val now = System.currentTimeMillis()
        claims.getOrPut(cityId) { ConcurrentHashMap() }[player] = now
        plugin.launchAsync {
            val sql = if (plugin.databaseManager.databaseType == DatabaseManager.DatabaseType.MYSQL) {
                "INSERT INTO elytra_claims (city_id, player_uuid, claimed_at) VALUES (?,?,?) " +
                    "ON DUPLICATE KEY UPDATE claimed_at = VALUES(claimed_at)"
            } else {
                "INSERT INTO elytra_claims (city_id, player_uuid, claimed_at) VALUES (?,?,?) " +
                    "ON CONFLICT(city_id, player_uuid) DO UPDATE SET claimed_at = excluded.claimed_at"
            }
            try {
                withContext(Dispatchers.IO) {
                    plugin.databaseManager.connection.use { conn ->
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setInt(1, cityId)
                            stmt.setString(2, player.toString())
                            stmt.setLong(3, now)
                            stmt.executeUpdate()
                        }
                    }
                }
            } catch (e: Exception) {
                // A claim that didn't persist lets the player claim the same ship again.
                plugin.logger.warning("[ElytraClaims] record($cityId/$player) failed: ${e.message}")
                com.esmpfun.betterend.integrations.MetricsService.reportHandled(e, "elytra-claim")
            }
        }
    }

    /** Drops every claim for a city (admin reset / delete). Returns rows removed. */
    suspend fun clearCity(cityId: Int): Int = withContext(Dispatchers.IO) {
        claims.remove(cityId)
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM elytra_claims WHERE city_id = ?").use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ElytraClaims] clearCity($cityId) failed: ${e.message}")
            0
        }
    }

    // ── cost item (config-backed) ────────────────────────────────────────────

    /**
     * The configured claim cost, or null when claiming is free. The stack's
     * amount carries `elytra.cost.amount` (clamped to the item's max stack
     * size — the amount slider and this loader enforce the same rule).
     */
    fun costStack(): ItemStack? {
        val amount = plugin.config.getInt("elytra.cost.amount", 0)
        if (amount <= 0) return null
        val encoded = plugin.config.getString("elytra.cost.item", "") ?: ""
        if (encoded.isEmpty()) return null
        val stack = runCatching { ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)) }
            .getOrNull() ?: return null
        if (stack.type.isAir) return null
        stack.amount = amount.coerceAtMost(stack.maxStackSize)
        return stack
    }

    /** Persists a new cost item (a SINGLE item; amount lives in `elytra.cost.amount`). */
    fun saveCostItem(item: ItemStack) {
        val single = item.clone().apply { amount = 1 }
        plugin.config.set("elytra.cost.item", Base64.getEncoder().encodeToString(single.serializeAsBytes()))
        // Re-clamp the amount to the new item's stack size (a 16-stack item
        // can't cost 64) and keep at least 1 so the pick takes effect.
        val amount = plugin.config.getInt("elytra.cost.amount", 0).coerceIn(1, single.maxStackSize)
        plugin.config.set("elytra.cost.amount", amount)
        plugin.saveConfig()
    }

    /** The single-item cost stack for display/slider math (never null: falls
     *  back to a shulker shell so the picker always has something to show). */
    fun costItemOrDefault(): ItemStack {
        costStack()?.let { return it.clone().apply { amount = 1 } }
        val encoded = plugin.config.getString("elytra.cost.item", "") ?: ""
        if (encoded.isNotEmpty()) {
            runCatching { ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)) }
                .getOrNull()?.let { return it.apply { amount = 1 } }
        }
        return ItemStack(Material.SHULKER_SHELL)
    }
}
