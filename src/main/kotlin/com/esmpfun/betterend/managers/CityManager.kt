package com.esmpfun.betterend.managers

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.models.EndCity
import com.esmpfun.betterend.models.IntBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Location
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the in-memory city cache and all `cities`/`city_pieces` SQL. A server
 * has at most a handful of registered End Cities, so the cache is fully
 * preloaded with no eviction and spatial lookups are a linear scan.
 */
class CityManager(private val plugin: BetterEnd) {

    private val cache = ConcurrentHashMap<Int, EndCity>()

    /**
     * Per-city loot-cycle start (epoch ms; 0 = no active cycle). A cycle is a
     * lazily-evaluated per-city refresh window: the first player to loot a city
     * with no active (or an expired) cycle starts a new one, which clears
     * everyone's per-player copies so the city's loot is fresh again. No
     * scheduler - the timer is only ever checked on a container open - so
     * cities refresh staggered by when each was first looted, never all at once.
     */
    private val cycleStarts = ConcurrentHashMap<Int, AtomicLong>()

    /** Loads every city (with its pieces) into the cache. Call once at startup. */
    suspend fun preload() = withContext(Dispatchers.IO) {
        cache.clear()
        cycleStarts.clear()
        plugin.databaseManager.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, world, min_x, min_y, min_z, max_x, max_y, max_z, " +
                    "origin_x, origin_y, origin_z, created_at, last_reset, snapshot_file, loot_cycle_start, has_ship, ship_x, ship_y, ship_z, head_taken FROM cities"
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val id = rs.getInt("id")
                        val region = IntBox(
                            rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                            rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"),
                        )
                        cache[id] = EndCity(
                            id = id,
                            world = rs.getString("world"),
                            region = region,
                            origin = Triple(rs.getInt("origin_x"), rs.getInt("origin_y"), rs.getInt("origin_z")),
                            pieces = loadPieces(conn, id),
                            createdAt = rs.getLong("created_at"),
                            lastReset = rs.getLong("last_reset").takeIf { !rs.wasNull() },
                            snapshotFile = rs.getString("snapshot_file"),
                            hasShip = rs.getInt("has_ship") != 0,
                            shipAnchor = rs.getInt("ship_x").let { x ->
                                if (rs.wasNull()) null else Triple(x, rs.getInt("ship_y"), rs.getInt("ship_z"))
                            },
                            headTaken = rs.getInt("head_taken") != 0,
                        )
                        val cycle = rs.getLong("loot_cycle_start")
                        if (!rs.wasNull() && cycle > 0L) cycleStarts[id] = AtomicLong(cycle)
                    }
                }
            }
        }
        plugin.logger.info("Loaded ${cache.size} End ${if (cache.size == 1) "City" else "Cities"} into cache")
    }

    private fun loadPieces(conn: java.sql.Connection, cityId: Int): List<IntBox> {
        val out = mutableListOf<IntBox>()
        conn.prepareStatement(
            "SELECT min_x, min_y, min_z, max_x, max_y, max_z FROM city_pieces WHERE city_id = ?"
        ).use { stmt ->
            stmt.setInt(1, cityId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) out.add(
                    IntBox(
                        rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                        rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"),
                    )
                )
            }
        }
        return out
    }

    /** Whether a city with this origin already exists (dedup guard for discovery). */
    suspend fun existsAt(world: String, origin: Triple<Int, Int, Int>): Boolean =
        withContext(Dispatchers.IO) {
            cache.values.any { it.world == world && it.origin == origin } || run {
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(
                        "SELECT 1 FROM cities WHERE world = ? AND origin_x = ? AND origin_y = ? AND origin_z = ?"
                    ).use { stmt ->
                        stmt.setString(1, world)
                        stmt.setInt(2, origin.first); stmt.setInt(3, origin.second); stmt.setInt(4, origin.third)
                        stmt.executeQuery().use { it.next() }
                    }
                }
            }
        }

    /**
     * Persists a newly-discovered city and its pieces, caches it, and returns
     * it - or null if a row with this origin already exists (UNIQUE collision,
     * e.g. a concurrent discovery). Idempotent against double-fire.
     */
    suspend fun registerCity(
        world: String,
        region: IntBox,
        origin: Triple<Int, Int, Int>,
        pieces: List<IntBox>,
    ): EndCity? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val cityId = conn.prepareStatement(
                        "INSERT INTO cities (world, min_x, min_y, min_z, max_x, max_y, max_z, " +
                            "origin_x, origin_y, origin_z, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS
                    ).use { stmt ->
                        stmt.setString(1, world)
                        stmt.setInt(2, region.minX); stmt.setInt(3, region.minY); stmt.setInt(4, region.minZ)
                        stmt.setInt(5, region.maxX); stmt.setInt(6, region.maxY); stmt.setInt(7, region.maxZ)
                        stmt.setInt(8, origin.first); stmt.setInt(9, origin.second); stmt.setInt(10, origin.third)
                        stmt.setLong(11, now)
                        stmt.executeUpdate()
                        stmt.generatedKeys.use { if (it.next()) it.getInt(1) else error("no generated city id") }
                    }
                    conn.prepareStatement(
                        "INSERT INTO city_pieces (city_id, min_x, min_y, min_z, max_x, max_y, max_z) VALUES (?,?,?,?,?,?,?)"
                    ).use { stmt ->
                        for (p in pieces) {
                            stmt.setInt(1, cityId)
                            stmt.setInt(2, p.minX); stmt.setInt(3, p.minY); stmt.setInt(4, p.minZ)
                            stmt.setInt(5, p.maxX); stmt.setInt(6, p.maxY); stmt.setInt(7, p.maxZ)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                    conn.commit()
                    val city = EndCity(cityId, world, region, origin, pieces, now)
                    cache[cityId] = city
                    city
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        } catch (e: Exception) {
            // Likely a UNIQUE collision from a concurrent discovery - treat as already-registered.
            plugin.logger.warning("[CityManager] registerCity failed for $world @ $origin: ${e.message}")
            null
        }
    }

    /**
     * Atomically decides whether THIS call should start a new loot cycle for
     * [cityId]: true when there's no active cycle or the current one is older
     * than [refreshMs]. Exactly one concurrent caller wins (CAS), so only one
     * clears the city's copies. The winner should call
     * [ContainerLootManager.clearCity] and [persistCycleStart]. Synchronous +
     * thread-safe; [refreshMs] <= 0 disables refresh entirely (always false).
     */
    fun beginCycleIfDue(cityId: Int, refreshMs: Long): Boolean {
        if (refreshMs <= 0L) return false
        val al = cycleStarts.getOrPut(cityId) { AtomicLong(0L) }
        while (true) {
            val cur = al.get()
            val now = System.currentTimeMillis()
            if (cur != 0L && now - cur < refreshMs) return false // active cycle, not due
            if (al.compareAndSet(cur, now)) return true          // we started the new cycle
            // lost the race - another thread advanced it; re-read and re-check
        }
    }

    /**
     * The current loot-cycle start for [cityId] (epoch ms; 0 = none). The
     * per-refresh elytra claim mode compares a claim's timestamp against this.
     */
    fun cycleStart(cityId: Int): Long = cycleStarts[cityId]?.get() ?: 0L

    /**
     * Force-starts a fresh loot cycle NOW (admin reset path), regardless of the
     * refresh window. Callers should also clear per-player copies + persist.
     */
    fun forceNewCycle(cityId: Int) {
        cycleStarts.getOrPut(cityId) { AtomicLong(0L) }.set(System.currentTimeMillis())
    }

    /** Persists the current in-memory cycle start for [cityId] to the DB. */
    suspend fun persistCycleStart(cityId: Int) = withContext(Dispatchers.IO) {
        val value = cycleStarts[cityId]?.get() ?: return@withContext
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET loot_cycle_start = ? WHERE id = ?").use { stmt ->
                    stmt.setLong(1, value)
                    stmt.setInt(2, cityId)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] persistCycleStart($cityId) failed: ${e.message}")
        }
    }

    /** Records a reset timestamp on a city (DB + cache). */
    suspend fun setLastReset(id: Int, at: Long) = withContext(Dispatchers.IO) {
        if (!cache.containsKey(id)) return@withContext
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET last_reset = ? WHERE id = ?").use { stmt ->
                    stmt.setLong(1, at)
                    stmt.setInt(2, id)
                    stmt.executeUpdate()
                }
            }
            cache.computeIfPresent(id) { _, c -> c.copy(lastReset = at) }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setLastReset($id) failed: ${e.message}")
        }
    }

    /**
     * Marks a city as (not) containing a ship (DB + cache). Set true when the
     * ship's unique dragon-head block is seen during snapshot capture, or when
     * its elytra frame is first identified. Never flips back to false
     * automatically - a harvested dragon head doesn't un-ship the city.
     */
    suspend fun setHasShip(id: Int, hasShip: Boolean) = withContext(Dispatchers.IO) {
        val city = cache[id] ?: return@withContext
        if (city.hasShip == hasShip) return@withContext
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET has_ship = ? WHERE id = ?").use { stmt ->
                    stmt.setInt(1, if (hasShip) 1 else 0)
                    stmt.setInt(2, id)
                    stmt.executeUpdate()
                }
            }
            cache.computeIfPresent(id) { _, c -> c.copy(hasShip = hasShip) }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setHasShip($id) failed: ${e.message}")
        }
    }

    /** Remembers where the ship is, the first time it's seen. Also marks the city as having one. */
    suspend fun setShipAnchor(id: Int, x: Int, y: Int, z: Int) = withContext(Dispatchers.IO) {
        val city = cache[id] ?: return@withContext
        if (city.shipAnchor != null) return@withContext
        cache.computeIfPresent(id) { _, c -> c.copy(shipAnchor = Triple(x, y, z), hasShip = true) }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET ship_x = ?, ship_y = ?, ship_z = ?, has_ship = 1 WHERE id = ?").use { stmt ->
                    stmt.setInt(1, x); stmt.setInt(2, y); stmt.setInt(3, z)
                    stmt.setInt(4, id)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setShipAnchor($id) failed: ${e.message}")
        }
    }

    /** Marks the ship's dragon head as taken for good (DB + cache). */
    suspend fun setHeadTaken(id: Int) = withContext(Dispatchers.IO) {
        val city = cache[id] ?: return@withContext
        if (city.headTaken) return@withContext
        cache.computeIfPresent(id) { _, c -> c.copy(headTaken = true) }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET head_taken = 1 WHERE id = ?").use { stmt ->
                    stmt.setInt(1, id)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setHeadTaken($id) failed: ${e.message}")
        }
    }

    /** Records the snapshot file name on a city (DB + cache). */
    suspend fun setSnapshotFile(id: Int, fileName: String?) = withContext(Dispatchers.IO) {
        if (!cache.containsKey(id)) return@withContext
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET snapshot_file = ? WHERE id = ?").use { stmt ->
                    stmt.setString(1, fileName)
                    stmt.setInt(2, id)
                    stmt.executeUpdate()
                }
            }
            cache.computeIfPresent(id) { _, c -> c.copy(snapshotFile = fileName) }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setSnapshotFile($id) failed: ${e.message}")
        }
    }

    /** The city whose region envelope contains [loc], or null. */
    fun getCachedCityAt(loc: Location): EndCity? =
        cache.values.firstOrNull { it.containsInRegion(loc) }

    /** The city whose region envelope, expanded by [pad], contains [loc]. */
    fun getCachedCityInPaddedRegion(loc: Location, pad: Int): EndCity? =
        cache.values.firstOrNull { it.containsInPaddedRegion(loc, pad) }

    fun byId(id: Int): EndCity? = cache[id]

    /** All cached cities (read-only snapshot). */
    fun all(): Collection<EndCity> = cache.values.toList()

    suspend fun deleteCity(id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM cities WHERE id = ?").use { stmt ->
                    stmt.setInt(1, id)
                    val removed = stmt.executeUpdate() > 0
                    if (removed) {
                        cache.remove(id)
                        cycleStarts.remove(id)
                    }
                    removed
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] deleteCity($id) failed: ${e.message}")
            false
        }
    }
}
