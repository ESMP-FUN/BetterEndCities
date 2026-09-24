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
 * Elytra claims and prices. Every claim is kept in memory because the punch
 * handler has to answer synchronously; the database is written behind it.
 */
class ElytraClaimManager(private val plugin: BetterEnd) {

    enum class ClaimMode(val key: String) {
        PER_SHIP("per-ship"),
        PER_REFRESH("per-refresh"),
        GLOBAL("global");

        companion object {
            fun fromConfig(raw: String?): ClaimMode =
                entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: PER_SHIP
        }
    }

    private companion object {
        /** 2^16 times the base price is already out of reach; this just stops the numbers overflowing. */
        const val MAX_DOUBLINGS = 16
    }

    /** cityId -> (player -> claimed_at). */
    private val claims = ConcurrentHashMap<Int, ConcurrentHashMap<UUID, Long>>()

    fun mode(): ClaimMode = ClaimMode.fromConfig(plugin.config.getString("elytra.claim-mode"))

    /** Call once at startup. */
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

    /** Whether the configured claim mode stops [player] claiming at [cityId]. */
    fun hasClaimed(cityId: Int, player: UUID): Boolean = when (mode()) {
        ClaimMode.PER_SHIP -> claims[cityId]?.containsKey(player) == true
        ClaimMode.PER_REFRESH -> {
            val at = claims[cityId]?.get(player)
            at != null && at >= plugin.cityManager.cycleStart(cityId)
        }
        ClaimMode.GLOBAL -> claims.values.any { it.containsKey(player) }
    }

    /** Records a claim now and writes it behind; a re-claim moves claimed_at forward. */
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

    /** Returns rows removed. */
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

    /** For a deleted city, whose rows the database already removed. */
    fun dropCity(cityId: Int) {
        claims.remove(cityId)
    }

    // ── price ────────────────────────────────────────────────────────────────

    /** What [player]'s next claim costs: [items] of the single [item] (null = no item) plus [levels]. */
    data class Price(val levels: Int, val item: ItemStack?, val items: Int, val doublings: Int)

    /** One loot refresh; 0 when refresh is off, which means forever. */
    fun doublingWindowMs(): Long = plugin.config.getInt("loot.refresh-hours", 12) * 3_600_000L

    /** Claims [player] made, across every ship, inside the doubling window. */
    fun recentClaims(player: UUID): Int {
        val window = doublingWindowMs()
        val since = if (window > 0) System.currentTimeMillis() - window else Long.MIN_VALUE
        return claims.values.count { (it[player] ?: return@count false) > since }
    }

    fun priceFor(player: UUID): Price {
        val doublings = if (plugin.config.getBoolean("elytra.cost.double-each-claim", false))
            recentClaims(player).coerceAtMost(MAX_DOUBLINGS) else 0
        val factor = 1 shl doublings
        val levels = (plugin.config.getInt("elytra.cost.levels", 0).coerceAtLeast(0).toLong() * factor)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        // Kept apart from the stack: a doubled count can pass what one stack may hold.
        val base = costStack()
        return Price(levels, base?.clone()?.apply { amount = 1 }, (base?.amount ?: 0) * factor, doublings)
    }

    // ── cost item ────────────────────────────────────────────────────────────

    /** The cost item with `elytra.cost.amount` as its amount, or null when there is none. */
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

    /** Saves one of [item]; the amount stays in `elytra.cost.amount`. */
    fun saveCostItem(item: ItemStack) {
        val single = item.clone().apply { amount = 1 }
        plugin.config.set("elytra.cost.item", Base64.getEncoder().encodeToString(single.serializeAsBytes()))
        // At least 1 so the pick takes effect, at most one stack of the new item.
        val amount = plugin.config.getInt("elytra.cost.amount", 0).coerceIn(1, single.maxStackSize)
        plugin.config.set("elytra.cost.amount", amount)
        plugin.saveConfig()
    }

    /** One of the cost item, or a shulker shell so the picker always shows something. */
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
